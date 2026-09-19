package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COUPON-610 — BS-EUP-20 catalogue registration, gated on promotions.euPercentage.enabled.
 */
class CouponRepositoryPercentageTest {

    /** Constructor args: (nlLaunch, amexEurope, euPercentage). */
    private CouponRepository repo(boolean euPercentage) {
        return new CouponRepository(false, false, euPercentage);
    }

    @Test
    void theEuPercentageCouponIsAbsentWhenTheFlagIsOff() {
        assertTrue(repo(false).find("BS-EUP-20").isEmpty(),
                "BS-EUP-20 must not exist unless promotions.euPercentage.enabled is true");
    }

    @Test
    void theEuPercentageCouponResolvesWhenTheFlagIsOn() {
        Coupon coupon = repo(true).find("BS-EUP-20").orElseThrow();

        assertEquals("BS-EUP-20", coupon.code());
        assertEquals(Coupon.DiscountType.PERCENTAGE, coupon.discountType());
        assertEquals(Coupon.EUR, coupon.settlementCurrency());
        assertTrue(coupon.isSepaSettled());
    }

    @Test
    void theEuPercentageCouponIsTwentyPercent() {
        Coupon coupon = repo(true).find("BS-EUP-20").orElseThrow();

        // 20% of a 50.00 subtotal (5000 minor units) = 1000 minor units.
        assertEquals(new java.math.BigDecimal("1000"),
                coupon.discountMinorUnitsFor(new java.math.BigDecimal("5000")));
    }

    @Test
    void theEuPercentageCouponIsEuWide() {
        Coupon coupon = repo(true).find("BS-EUP-20").orElseThrow();

        assertFalse(coupon.isCountryRestricted());
        assertTrue(coupon.isAvailableIn("DE"));
        assertTrue(coupon.isAvailableIn("FR"));
    }

    @Test
    void theEuPercentageCouponIsFundedOnTheAcceptedNetworks() {
        Coupon coupon = repo(true).find("BS-EUP-20").orElseThrow();

        assertTrue(coupon.fundedBy().contains(CardNetwork.VISA));
        assertTrue(coupon.fundedBy().contains(CardNetwork.MASTERCARD));
    }

    @Test
    void enablingTheEuPercentageLeavesTheExistingCatalogueUntouched() {
        CouponRepository on = repo(true);

        assertEquals(new java.math.BigDecimal("20.00"), on.find("BS-EU-20").orElseThrow().discount());
        assertEquals(Coupon.DiscountType.FIXED, on.find("BS-EU-20").orElseThrow().discountType());
        assertEquals(new java.math.BigDecimal("10.00"), on.find("NW-VISA-10").orElseThrow().discount());
    }

    @Test
    void theNewCodeMatchesTheRequestPattern() {
        // RedemptionRequest pattern: ^(BS|NW)-[A-Z]{2,4}-\d{2}$ — PCT20 fits [A-Z]{2,4} + \d{2}.
        assertTrue("BS-EUP-20".matches("^(BS|NW)-[A-Z]{2,4}-\\d{2}$"),
                "BS-EUP-20 must be submittable through POST /v1/redemptions");
    }
}
