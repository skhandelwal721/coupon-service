package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COUPON-612 — the arguments {@code discountMinorUnitsFor} did not guard.
 */
class CouponSubtotalGuardTest {

    private static Coupon rate() {
        return Coupon.percentage("BS-EUP-20", 2000, Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), Set.of());
    }

    private static Coupon amount() {
        return new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD));
    }

    /** Previously a NullPointerException out of multiply(). */
    @Test
    void aNullSubtotalAnswersZero() {
        assertEquals(BigDecimal.ZERO, rate().discountMinorUnitsFor(null));
    }

    /** Previously a negative result, which is not a discount. */
    @Test
    void aNegativeSubtotalAnswersZero() {
        assertEquals(BigDecimal.ZERO, rate().discountMinorUnitsFor(new BigDecimal("-1")));
        assertEquals(BigDecimal.ZERO, rate().discountMinorUnitsFor(new BigDecimal("-24900")));
    }

    @Test
    void aZeroSubtotalAnswersZero() {
        assertEquals(BigDecimal.ZERO, rate().discountMinorUnitsFor(BigDecimal.ZERO));
    }

    @Test
    void theGuardedAnswerHasTheSameScaleAsTheComputedAnswer() {
        BigDecimal guarded = rate().discountMinorUnitsFor(null);
        BigDecimal computed = rate().discountMinorUnitsFor(new BigDecimal("24900"));

        assertEquals(0, guarded.scale());
        assertEquals(0, computed.scale());
    }

    /** The guard does not disturb the arithmetic it guards. */
    @Test
    void positiveSubtotalsAreUnaffected() {
        Coupon coupon = rate();

        assertEquals(new BigDecimal("4980"), coupon.discountMinorUnitsFor(new BigDecimal("24900")));
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(new BigDecimal("10000")));
        assertEquals(new BigDecimal("200"), coupon.discountMinorUnitsFor(new BigDecimal("1001")));
        assertEquals(new BigDecimal("1"), coupon.discountMinorUnitsFor(new BigDecimal("9")));
    }

    /** A FIXED coupon returns its own amount and never reaches the guard. */
    @Test
    void fixedCouponsAreUnaffectedByTheGuard() {
        Coupon coupon = amount();

        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(null));
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(new BigDecimal("-1")));
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnitsFor(new BigDecimal("24900")));
    }

    @Test
    void callersCanAskWhetherDiscountMinorUnitsWillAnswer() {
        assertTrue(amount().hasFixedAmount());
        assertFalse(rate().hasFixedAmount());

        // The predicate agrees with the method it describes.
        assertEquals(new BigDecimal("2000"), amount().discountMinorUnits());
    }
}
