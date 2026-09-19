package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COUPON-610 — percentage discounting, introduced to the catalogue for the first time.
 */
class CouponPercentageTest {

    private static Coupon pct(int bps) {
        return Coupon.percentage("BS-EUP-20", bps, Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), Set.of());
    }

    @Test
    void aPercentageCouponComputesItsDiscountFromTheSubtotal() {
        Coupon coupon = pct(2000); // 20%

        // 249.00 subtotal = 24900 minor units; 20% = 4980 minor units.
        assertEquals(new BigDecimal("4980"), coupon.discountMinorUnitsFor(new BigDecimal("24900")));
        // 100.00 = 10000 minor units; 20% = 2000.
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(new BigDecimal("10000")));
    }

    @Test
    void aPercentageDiscountTruncatesRatherThanRoundsUp() {
        Coupon coupon = pct(2000); // 20%

        // 1001 minor units * 20% = 200.2 -> truncated to 200. We never instruct more than agreed.
        assertEquals(new BigDecimal("200"), coupon.discountMinorUnitsFor(new BigDecimal("1001")));
    }

    @Test
    void aPercentageCouponHasNoFixedMinorUnits() {
        Coupon coupon = pct(2000);

        assertEquals(Coupon.DiscountType.PERCENTAGE, coupon.discountType());
        assertThrows(IllegalStateException.class, coupon::discountMinorUnits,
                "a PERCENTAGE coupon has no fixed amount; callers must pass the subtotal");
    }

    @Test
    void aPercentageRateMustBeWithinBounds() {
        assertThrows(IllegalArgumentException.class, () -> pct(0));
        assertThrows(IllegalArgumentException.class, () -> pct(-100));
        assertThrows(IllegalArgumentException.class, () -> pct(10001));
        // 10000 bps (100%) is the boundary and is allowed.
        assertEquals(Coupon.DiscountType.PERCENTAGE, pct(10000).discountType());
    }

    /** The whole existing catalogue is FIXED and its behaviour is unchanged. */
    @Test
    void fixedCouponsAreUnchangedAndIgnoreSubtotal() {
        Coupon fixed = new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD));

        assertEquals(Coupon.DiscountType.FIXED, fixed.discountType());
        assertEquals(new BigDecimal("2000"), fixed.discountMinorUnits());
        // discountMinorUnitsFor returns the fixed amount regardless of subtotal.
        assertEquals(new BigDecimal("2000"), fixed.discountMinorUnitsFor(new BigDecimal("999999")));
    }

    @Test
    void theEuPercentageCouponIsEuWideNotCountryRestricted() {
        Coupon coupon = pct(2000);

        assertFalse(coupon.isCountryRestricted());
        assertTrue(coupon.isAvailableIn("DE"));
        assertTrue(coupon.isAvailableIn("FR"));
        assertTrue(coupon.isAvailableIn(null), "EU-wide coupon is available with or without a country");
    }
}
