package com.beaconstone.coupon.redemption;

import com.beaconstone.coupon.audit.RedemptionAuditor;
import com.beaconstone.coupon.billing.BillingChargeView;
import com.beaconstone.coupon.billing.BillingClient;
import com.beaconstone.coupon.billing.CardNetwork;
import com.beaconstone.coupon.ledger.PromotionLedger;
import com.beaconstone.coupon.promotion.Coupon;
import com.beaconstone.coupon.promotion.CouponRepository;
import com.beaconstone.coupon.promotion.NetworkPromotionRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Redeems a coupon against an invoice.
 *
 * <p>The order of operations is deliberate and every step depends on billing-service's
 * published charge contract:
 *
 * <ol>
 *   <li>Resolve the coupon, and check it is available in the request's country
 *       ({@link Coupon#isAvailableIn}). This is before the charge on purpose — a coupon that
 *       is not offered in this country must not take a card charge that would then have to be
 *       refunded.</li>
 *   <li>Charge the invoice through billing-service and read the charge back
 *       ({@link BillingClient}).</li>
 *   <li>Check the charge is accountable — {@code subtotal + tax == total}
 *       ({@link RedemptionAuditor}).</li>
 *   <li>Resolve the funding network from {@code cardType} and check the coupon is funded on
 *       that network ({@link NetworkPromotionRules}).</li>
 *   <li>Book the discount into the promotion liability ledger
 *       ({@link PromotionLedger}).</li>
 * </ol>
 *
 * <p>Steps 3 and 4 are the funding gates. Neither has a default branch: a charge we cannot
 * account for, or a network we cannot attribute, holds the redemption. We would rather hold a
 * discount than book one against a charge we do not understand. Step 1's country gate is the
 * one gate that fires <em>before</em> money moves, because it needs no charge to decide.
 */
@Service
public class RedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionService.class);

    private final BillingClient billingClient;
    private final CouponRepository couponRepository;
    private final NetworkPromotionRules promotionRules;
    private final RedemptionAuditor auditor;
    private final PromotionLedger promotionLedger;

    /**
     * COUPON-610 kill switch for the PERCENTAGE redemption path.
     *
     * <p>Distinct from {@code promotions.euPercentage.enabled}, which controls whether a
     * percentage coupon is in the <em>catalogue</em>. This flag controls whether the new
     * FIXED-vs-PERCENTAGE branching in {@link #redeem} actually runs the percentage logic. With
     * it off, a percentage coupon is refused <strong>before any charge</strong> rather than
     * exercising the new pre-charge/subtotal logic — so the new logic can be disabled at runtime
     * without a code revert, and independently of the catalogue flag. Defaults to false.
     */
    private final boolean percentageRedemptionEnabled;

    public RedemptionService(BillingClient billingClient,
                             CouponRepository couponRepository,
                             NetworkPromotionRules promotionRules,
                             RedemptionAuditor auditor,
                             PromotionLedger promotionLedger,
                             @Value("${promotions.euPercentage.redemptionEnabled:false}")
                             boolean percentageRedemptionEnabled) {
        this.billingClient = billingClient;
        this.couponRepository = couponRepository;
        this.promotionRules = promotionRules;
        this.auditor = auditor;
        this.promotionLedger = promotionLedger;
        this.percentageRedemptionEnabled = percentageRedemptionEnabled;
    }

    public RedemptionReceipt redeem(RedemptionRequest request) {
        Coupon coupon = couponRepository.find(request.couponCode())
                .orElseThrow(() -> new UnknownCouponException(request.couponCode()));

        // COUPON-573: a country-restricted coupon (e.g. the Netherlands-only BS-NL-20) may only
        // be redeemed from a country it is offered in. Checked before the charge so a shopper on
        // the wrong storefront is refused rather than charged and refunded. Unrestricted coupons
        // pass this unconditionally, so the existing catalogue is unaffected.
        if (!coupon.isAvailableIn(request.billingCountry())) {
            throw new CouponNotAvailableInCountryException(coupon.code(), request.billingCountry());
        }

        // COUPON-610 kill switch: the FIXED-vs-PERCENTAGE branching below is new logic. It only
        // runs the percentage path when percentageRedemptionEnabled is on. When it is off, a
        // percentage coupon is refused HERE — before any charge — so the new pre-charge/subtotal
        // logic is never exercised without the switch, and it can be turned off at runtime
        // without a code revert. FIXED coupons are unaffected by this gate.
        if (coupon.discountType() == Coupon.DiscountType.PERCENTAGE && !percentageRedemptionEnabled) {
            throw new PercentageRedemptionDisabledException(coupon.code());
        }

        // COUPON-530: send the promotional deduction with the charge. billing-service applies
        // it to the invoice subtotal, so a discounted order is one card transaction instead of
        // a full charge followed by a refund for the difference — one statement line, one
        // interchange fee.
        //
        // COUPON-610: a FIXED coupon's deduction is a constant known before the charge, so it is
        // sent as today. A PERCENTAGE coupon's deduction is a function of the subtotal, which
        // only billing-service knows authoritatively; we send a zero pre-adjustment so the charge
        // establishes the true subtotal, then compute the percentage from that subtotal below.
        // This keeps the single-transaction model for FIXED unchanged and derives the percentage
        // from the same subtotal that reconciliation uses.
        java.math.BigDecimal preChargeAdjustment = coupon.discountType() == Coupon.DiscountType.FIXED
                ? coupon.discountMinorUnits()
                : java.math.BigDecimal.ZERO;

        BillingChargeView charge = billingClient.charge(
                request.invoiceId(), request.cardNumber(), request.currency(),
                request.billingPostcode(), preChargeAdjustment);

        auditor.requireAccountable(charge);

        if (!promotionRules.isEligible(coupon, charge)) {
            throw new PromotionNotFundedException(
                    "coupon " + coupon.code() + " is not funded on this network");
        }

        CardNetwork network = promotionRules.fundingNetwork(charge);
        String redemptionId = "rdm_" + UUID.randomUUID();

        // Minor units, so one settlement pipeline covers Bacs/FPS and SEPA. See Coupon.
        // FIXED returns its constant amount; PERCENTAGE is computed against the charge subtotal
        // (in minor units) that billing-service returned, so the booked discount matches what
        // reconciliation will see.
        java.math.BigDecimal subtotalMinorUnits = charge.subtotal()
                .multiply(new java.math.BigDecimal("100"))
                .setScale(0, java.math.RoundingMode.DOWN);
        java.math.BigDecimal discountMinorUnits = coupon.discountMinorUnitsFor(subtotalMinorUnits);

        log.info("redeemed redemptionId={} couponCode={} chargeId={} network={} currency={} "
                        + "discountMinorUnits={} sepaSettled={}",
                redemptionId, coupon.code(), charge.chargeId(), network,
                coupon.settlementCurrency(), discountMinorUnits, coupon.isSepaSettled());

        RedemptionReceipt receipt = new RedemptionReceipt(
                redemptionId,
                coupon.code(),
                charge.chargeId(),
                network.name(),
                discountMinorUnits,
                RedemptionReceipt.MINOR_UNITS,
                coupon.settlementCurrency(),
                request.customerIp(),
                "REDEEMED");

        promotionLedger.book(receipt);

        return receipt;
    }

    public static class UnknownCouponException extends RuntimeException {
        public UnknownCouponException(String code) {
            super("no such coupon: " + code);
        }
    }

    public static class PromotionNotFundedException extends RuntimeException {
        public PromotionNotFundedException(String message) {
            super(message);
        }
    }

    /**
     * COUPON-610. A percentage coupon was redeemed while the percentage redemption path is
     * disabled ({@code promotions.euPercentage.redemptionEnabled} off). Thrown before any charge
     * — the kill switch stops the new logic before money moves.
     */
    public static class PercentageRedemptionDisabledException extends RuntimeException {
        public PercentageRedemptionDisabledException(String code) {
            super("percentage redemption is disabled; coupon " + code + " cannot be redeemed");
        }
    }

    /**
     * A country-restricted coupon was redeemed from a country it is not offered in (or with no
     * country at all). No charge is taken — this fires before billing-service is called.
     */
    public static class CouponNotAvailableInCountryException extends RuntimeException {
        public CouponNotAvailableInCountryException(String code, String country) {
            super("coupon " + code + " is not available in country "
                    + (country == null || country.isBlank() ? "<none>" : country));
        }
    }
}
