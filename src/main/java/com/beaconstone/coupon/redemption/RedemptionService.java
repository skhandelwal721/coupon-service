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
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Redeems a coupon against an invoice.
 *
 * <p>The order of operations is deliberate and every step depends on billing-service's
 * published charge contract:
 *
 * <ol>
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
 * <p>Steps 2 and 3 are the gates. Neither has a default branch: a charge we cannot account
 * for, or a network we cannot attribute, holds the redemption. We would rather hold a discount
 * than book one against a charge we do not understand.
 */
@Service
public class RedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionService.class);

    private final BillingClient billingClient;
    private final CouponRepository couponRepository;
    private final NetworkPromotionRules promotionRules;
    private final RedemptionAuditor auditor;
    private final PromotionLedger promotionLedger;

    public RedemptionService(BillingClient billingClient,
                             CouponRepository couponRepository,
                             NetworkPromotionRules promotionRules,
                             RedemptionAuditor auditor,
                             PromotionLedger promotionLedger) {
        this.billingClient = billingClient;
        this.couponRepository = couponRepository;
        this.promotionRules = promotionRules;
        this.auditor = auditor;
        this.promotionLedger = promotionLedger;
    }

    public RedemptionReceipt redeem(RedemptionRequest request) {
        Coupon coupon = couponRepository.find(request.couponCode())
                .orElseThrow(() -> new UnknownCouponException(request.couponCode()));

        // A promotion is redeemable only in the currency it is denominated in. Refused, not
        // converted — see requireSameCurrency.
        requireSameCurrency(coupon, request);

        // billing-service needs the postcode to resolve the VAT place of supply. A cross-border
        // EUR supply is taxed in the customer's member state, not ours.
        BillingChargeView charge = billingClient.charge(
                request.invoiceId(), request.cardNumber(), request.currency(),
                request.billingPostcode());

        auditor.requireAccountable(charge);

        if (!promotionRules.isEligible(coupon, charge)) {
            throw new PromotionNotFundedException(
                    "coupon " + coupon.code() + " is not funded on this network");
        }

        CardNetwork network = promotionRules.fundingNetwork(charge);
        String redemptionId = "rdm_" + UUID.randomUUID();

        // One figure, one currency, derived once from the coupon's own amount — never from a
        // converted intermediate, and never one representation from the other (MFC-4).
        java.math.BigDecimal discountMinorUnits = coupon.discountMinorUnits();

        log.info("redeemed redemptionId={} couponCode={} chargeId={} network={} currency={} "
                        + "discountMinorUnits={}",
                redemptionId, coupon.code(), charge.chargeId(), network,
                coupon.settlementCurrency(), discountMinorUnits);

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

    /**
     * A promotion may only be redeemed in the currency it is denominated in.
     *
     * <p>Refused rather than converted, and the refusal is the fix. COUPON-510 converted at
     * redemption, and every defect it caused followed from that one decision:
     *
     * <ul>
     *   <li>the receipt carried a converted amount beside the promotion's own currency label,
     *       so the label contradicted the figure (MFC-2.3);</li>
     *   <li>{@code order-service} has no currency field on the receipt and subtracted the
     *       converted figure from a subtotal in another currency — a silent mispricing on the
     *       storefront checkout path (MFC-6.3);</li>
     *   <li>the promotion ledger and the finance attribution export received amounts in two
     *       currencies in one accumulator (MFC-3.1);</li>
     *   <li>the charged total became a function of an FX rate, so which Strong Customer
     *       Authentication exemption threshold applied moved with the market (SCA-2.4).</li>
     * </ul>
     *
     * <p>Refusing is a visible 409 the storefront can act on: offer the shopper the equivalent
     * code for their storefront. Converting was silent and wrong in four places at once.
     *
     * <p>Cross-storefront redemption — the problem COUPON-510 set out to solve — belongs in the
     * catalogue: issue an equivalent code per currency. That keeps the amount, its label, the
     * ledger and the charge currency consistent, and it needs no runtime FX at all.
     */
    private static void requireSameCurrency(Coupon coupon, RedemptionRequest request) {
        if (!coupon.settlementCurrency().equals(request.currency())) {
            throw new CurrencyMismatchException(coupon.code(),
                    coupon.settlementCurrency(), request.currency());
        }
    }

    /** The promotion is not denominated in the currency the order is priced in. */
    public static class CurrencyMismatchException extends RuntimeException {
        public CurrencyMismatchException(String code, String couponCurrency,
                                         String orderCurrency) {
            super("coupon " + code + " is a " + couponCurrency + " promotion and cannot be"
                    + " redeemed against an order priced in " + orderCurrency);
        }
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
}
