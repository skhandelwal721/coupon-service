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

        // The invoice is charged in full. The discount reaches the customer through the refund
        // leg, and that is deliberate — see docs/api/promotional-adjustment-prerequisites.md.
        // Deducting it here instead reduces the payable amount twice, because order-service
        // subtracts it again from what it shows the shopper.
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

        // Minor units, so one settlement pipeline covers Bacs/FPS and SEPA. See Coupon.
        java.math.BigDecimal discountMinorUnits = coupon.discountMinorUnits();

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
}
