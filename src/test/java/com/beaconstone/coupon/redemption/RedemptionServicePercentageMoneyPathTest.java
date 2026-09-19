package com.beaconstone.coupon.redemption;

import com.beaconstone.coupon.audit.RedemptionAuditor;
import com.beaconstone.coupon.billing.BillingChargeView;
import com.beaconstone.coupon.billing.BillingClient;
import com.beaconstone.coupon.billing.CardMask;
import com.beaconstone.coupon.billing.CardNetwork;
import com.beaconstone.coupon.ledger.PromotionLedger;
import com.beaconstone.coupon.promotion.Coupon;
import com.beaconstone.coupon.promotion.CouponRepository;
import com.beaconstone.coupon.promotion.NetworkPromotionRules;
import com.beaconstone.coupon.sepa.SepaAddressNormaliser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COUPON-610 fix — the tier-1 money-path regression.
 *
 * <p>The defect: a PERCENTAGE coupon computed its discount from the charge subtotal and booked
 * it to the {@link PromotionLedger}, but never applied it to the card. On a 249.00 EUR order
 * BS-EUP-20 booked 49.80 EUR of liability to the ledger while the customer's card was charged
 * the full 249.00 EUR — a reconciliation break on every redemption and a "discount applied"
 * message against an undiscounted statement.
 *
 * <p>These tests pin the invariant that closes it: the amount taken off the card must equal the
 * amount booked to the ledger, and a billing-service that fails to apply the discount must hold
 * the redemption rather than book a phantom liability.
 */
class RedemptionServicePercentageMoneyPathTest {

    private static final BigDecimal FULL_SUBTOTAL = new BigDecimal("249.00");
    private static final BigDecimal TAX = new BigDecimal("49.80");

    /** A BillingClient whose first charge is the full-price fixture and whose adjustment leg is observable. */
    private static class RecordingBillingClient extends BillingClient {
        private final boolean applyDiscount;
        BigDecimal appliedDiscountMinorUnits;
        BillingChargeView lastReturnedCharge;

        RecordingBillingClient(boolean applyDiscount) {
            super("http://billing.test", "/v1/invoices/{invoiceId}/charge",
                    new SepaAddressNormaliser(), new CardMask());
            this.applyDiscount = applyDiscount;
        }

        @Override
        public BillingChargeView charge(String invoiceId, String cardNumber, String currency,
                                        String billingPostcode, BigDecimal promotionalAdjustment) {
            // Full-price charge: percentage coupons send a zero pre-adjustment, so the card is
            // established at the full subtotal and the discount must be applied afterwards.
            BigDecimal subtotal = FULL_SUBTOTAL.subtract(promotionalAdjustment.movePointLeft(2));
            lastReturnedCharge = new BillingChargeView(
                    "chg_test", invoiceId, subtotal, TAX, subtotal.add(TAX),
                    currency, "VISA", "wp_test", "CHARGED");
            return lastReturnedCharge;
        }

        @Override
        public BillingChargeView applyPromotionalDiscount(BillingChargeView charge,
                                                          BigDecimal discountMinorUnits) {
            this.appliedDiscountMinorUnits = discountMinorUnits;
            if (!applyDiscount) {
                // Simulate billing-service failing to apply the deduction: the card is
                // unchanged. The service must catch this and hold the redemption.
                return charge;
            }
            lastReturnedCharge = super.applyPromotionalDiscount(charge, discountMinorUnits);
            return lastReturnedCharge;
        }
    }

    private static CouponRepository repoWith(Coupon coupon) {
        // CouponRepository is a concrete class with an overridable find(); subclass it so the
        // test drives exactly the coupon under test rather than depending on feature flags.
        return new CouponRepository() {
            @Override
            public Optional<Coupon> find(String code) {
                return coupon.code().equals(code) ? Optional.of(coupon) : Optional.empty();
            }
        };
    }

    private static RedemptionRequest euRequest() {
        return new RedemptionRequest(
                "BS-EUP-20", "inv_1", "4111111111111111", "EUR",
                "1011AB", "device-1", "203.0.113.7", "shopper@example.com", "DE");
    }

    private static Coupon euPercentageCoupon() {
        return Coupon.percentage("BS-EUP-20", 2000, Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), Set.of());
    }

    @Test
    void percentageDiscountIsAppliedToTheCardNotJustBookedToTheLedger() {
        RecordingBillingClient billing = new RecordingBillingClient(true);
        PromotionLedger ledger = new PromotionLedger();
        RedemptionService service = new RedemptionService(
                billing, repoWith(euPercentageCoupon()), new NetworkPromotionRules(),
                new RedemptionAuditor(), ledger, /* percentageRedemptionEnabled */ true);

        RedemptionReceipt receipt = service.redeem(euRequest());

        // 20% of 249.00 (24900 minor units) = 49.80 (4980 minor units).
        assertEquals(new BigDecimal("4980"), receipt.discount(),
                "the receipt must carry the computed percentage discount");

        // The discount was actually pushed to billing-service to reduce the card.
        assertEquals(new BigDecimal("4980"), billing.appliedDiscountMinorUnits,
                "the computed discount must be sent to billing to reduce the card charge");

        // The card was settled at the reduced subtotal — the defect charged the full 249.00.
        assertEquals(new BigDecimal("199.20"), billing.lastReturnedCharge.subtotal(),
                "the card must be settled at subtotal - discount, not the full price");

        // Ledger and card agree: the liability booked equals the money that moved.
        assertEquals(new BigDecimal("4980.00"), ledger.liabilityFor(CardNetwork.VISA),
                "the ledger must book exactly what was taken off the card");
    }

    @Test
    void redemptionIsHeldWhenBillingDoesNotApplyTheDiscount() {
        // billing-service accepts the adjustment call but does not actually reduce the charge.
        RecordingBillingClient billing = new RecordingBillingClient(false);
        PromotionLedger ledger = new PromotionLedger();
        RedemptionService service = new RedemptionService(
                billing, repoWith(euPercentageCoupon()), new NetworkPromotionRules(),
                new RedemptionAuditor(), ledger, /* percentageRedemptionEnabled */ true);

        assertThrows(RedemptionService.DiscountNotAppliedToChargeException.class,
                () -> service.redeem(euRequest()),
                "a discount that did not reduce the card must hold the redemption");

        // And nothing was booked — the ledger stays at zero, no phantom liability.
        assertTrue(ledger.liabilityFor(CardNetwork.VISA).compareTo(BigDecimal.ZERO.setScale(2)) == 0,
                "no liability may be booked when the discount was not applied to the card");
    }
}
