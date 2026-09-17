package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.BillingChargeView;
import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkPromotionRulesTest {

    private final NetworkPromotionRules rules = new NetworkPromotionRules();

    private final Coupon visaOnly =
            new Coupon("NW-VISA-10", new BigDecimal("10.00"), Set.of(CardNetwork.VISA));

    @Test
    void appliesAVisaFundedCouponToAVisaCharge() {
        assertTrue(rules.isEligible(visaOnly, charge("VISA")));
    }

    @Test
    void refusesAVisaFundedCouponOnAMastercardCharge() {
        assertFalse(rules.isEligible(visaOnly, charge("MASTERCARD")));
    }

    /**
     * Regression for COUPON-551 — the bug as a customer met it.
     *
     * <p>With the offer funded on Visa alone, a Mastercard shopper reached this check
     * <em>after</em> the charge had already been taken, and was refused here. The charge stood,
     * at the discounted amount, with no promotion booked against it.
     *
     * <p>Reads the entry from the catalogue rather than rebuilding it, so the test fails if the
     * catalogue regresses.
     */
    @Test
    void theEuAcquisitionCouponIsEligibleOnEitherFundedNetwork() {
        Coupon euOffer = new CouponRepository().find("BS-EU-20").orElseThrow();

        assertTrue(rules.isEligible(euOffer, charge("VISA")));
        assertTrue(rules.isEligible(euOffer, charge("MASTERCARD")));
    }

    /**
     * Guard rail for a change to what {@code cardType} means.
     *
     * <p>Every network billing-service can charge has to resolve to a funding network here,
     * otherwise a redemption on that network cannot be attributed and the promotion is
     * unfunded. Enumerates {@link CardNetwork#values()} deliberately rather than listing the
     * networks we happen to support today.
     */
    @Test
    void everyKnownNetworkResolvesToAFundingNetwork() {
        for (CardNetwork network : CardNetwork.values()) {
            assertDoesNotThrow(() -> rules.fundingNetwork(charge(network.name())),
                    "cannot attribute promotional funding for " + network);
        }
    }

    /**
     * A {@code cardType} that is not a network is not a default — it is a contract break.
     * Funding type values such as {@code CREDIT} or {@code CHARGE_CARD} land here.
     */
    @Test
    void refusesToGuessWhenCardTypeIsNotANetwork() {
        assertThrows(IllegalArgumentException.class,
                () -> rules.fundingNetwork(charge("CREDIT")));
        assertThrows(IllegalArgumentException.class,
                () -> rules.fundingNetwork(charge("CHARGE_CARD")));
    }

    private static BillingChargeView charge(String cardType) {
        return new BillingChargeView(
                "chg_1", "inv-1001",
                new BigDecimal("249.00"), new BigDecimal("49.80"), new BigDecimal("298.80"),
                "GBP", cardType, "wp_4f8a21c7", "CHARGED");
    }
}
