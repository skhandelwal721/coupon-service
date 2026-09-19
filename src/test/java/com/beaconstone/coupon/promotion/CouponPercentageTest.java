package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * COUPON-610 — the PERCENTAGE discount type and its arithmetic.
 */
class CouponPercentageTest {

    /** A 20% coupon, the rate the EU offer will use. */
    private static Coupon pct(int bps) {
        return Coupon.percentage("BS-EUP-20", bps, Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), Set.of());
    }

    private static Coupon fixed() {
        return new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD));
    }

    @Test
    void aRateIsAppliedToTheSubtotal() {
        Coupon coupon = pct(2000); // 20%

        // 249.00 subtotal = 24900 minor units; 20% = 4980.
        assertEquals(new BigDecimal("4980"), coupon.discountMinorUnitsFor(new BigDecimal("24900")));
        // 100.00 = 10000 minor units; 20% = 2000.
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(new BigDecimal("10000")));
        // A zero subtotal yields zero.
        assertEquals(new BigDecimal("0"), coupon.discountMinorUnitsFor(BigDecimal.ZERO));
    }

    @Test
    void aRateTruncatesRatherThanRoundsUp() {
        Coupon coupon = pct(2000);

        // 1001 * 20% = 200.2, truncated to 200 — the same direction discountMinorUnits() rounds.
        assertEquals(new BigDecimal("200"), coupon.discountMinorUnitsFor(new BigDecimal("1001")));
        // 9 * 20% = 1.8, truncated to 1.
        assertEquals(new BigDecimal("1"), coupon.discountMinorUnitsFor(new BigDecimal("9")));
        // 4 * 20% = 0.8, truncated to 0 — a fraction of a minor unit is never carried.
        assertEquals(new BigDecimal("0"), coupon.discountMinorUnitsFor(new BigDecimal("4")));
    }

    @Test
    void theRateMustBeWithinBounds() {
        assertThrows(IllegalArgumentException.class, () -> pct(0));
        assertThrows(IllegalArgumentException.class, () -> pct(-100));
        assertThrows(IllegalArgumentException.class, () -> pct(10001));

        // 10000 bps is the boundary and is allowed.
        assertEquals(Coupon.DiscountType.PERCENTAGE, pct(10000).discountType());
    }

    @Test
    void aPercentageCouponHasNoAmountWithoutASubtotal() {
        Coupon coupon = pct(2000);

        assertEquals(Coupon.DiscountType.PERCENTAGE, coupon.discountType());
        assertEquals(2000, coupon.percentageBps());
        assertThrows(IllegalStateException.class, coupon::discountMinorUnits,
                "answering 0 here would be a wrong figure, not a missing one");
    }

    /** The whole existing catalogue is FIXED, and nothing about it changes. */
    @Test
    void fixedCouponsAreUnchanged() {
        Coupon coupon = fixed();

        assertEquals(Coupon.DiscountType.FIXED, coupon.discountType());
        assertEquals(0, coupon.percentageBps());
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnits());
    }

    @Test
    void aFixedCouponIgnoresTheSubtotalWhenAskedThroughTheNewMethod() {
        Coupon coupon = fixed();

        // The one method serves both types: FIXED returns its own amount whatever it is given.
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(new BigDecimal("999999")));
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(new BigDecimal("1000")));
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(BigDecimal.ZERO));
    }

    @Test
    void theExistingConstructorsStillProduceFixedCoupons() {
        assertEquals(Coupon.DiscountType.FIXED,
                new Coupon("NW-VISA-10", new BigDecimal("10.00"), Set.of(CardNetwork.VISA))
                        .discountType());
        assertEquals(Coupon.DiscountType.FIXED,
                new Coupon("NW-SEPA-10", new BigDecimal("10.00"), Coupon.EUR,
                        Set.of(CardNetwork.VISA)).discountType());
        assertEquals(Coupon.DiscountType.FIXED,
                new Coupon("BS-NL-20", new BigDecimal("20.00"), Coupon.EUR,
                        Set.of(CardNetwork.VISA), Set.of("NL")).discountType());
    }
}
