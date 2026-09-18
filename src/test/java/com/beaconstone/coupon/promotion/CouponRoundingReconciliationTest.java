package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COUPON-610 — the discount rounding must stay reconciled with billing-service's charge
 * arithmetic. These tests pin the direction (truncate DOWN) so a future change that switches to
 * rounding up — which would instruct more promotional spend than the rate agrees against the
 * billed subtotal — fails here.
 */
class CouponRoundingReconciliationTest {

    private static Coupon pct(int bps) {
        return Coupon.percentage("BS-EUP-20", bps, Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), Set.of());
    }

    @Test
    void aPercentageDiscountNeverExceedsThePercentageOfTheBilledSubtotal() {
        Coupon coupon = pct(2000); // 20%

        // subtotal 1001 minor units => exact 20% is 200.2; truncated DOWN to 200, not up to 201.
        BigDecimal discount = coupon.discountMinorUnitsFor(new BigDecimal("1001"));
        assertEquals(new BigDecimal("200"), discount);

        // Reconciliation property: discount * 10000 <= subtotal * bps (never over the rate).
        assertTrue(discount.multiply(new BigDecimal("10000"))
                        .compareTo(new BigDecimal("1001").multiply(BigDecimal.valueOf(2000))) <= 0,
                "the booked discount must not exceed the agreed percentage of the billed subtotal");
    }

    @Test
    void roundingIsDownAcrossAwkwardSubtotals() {
        Coupon coupon = pct(2000); // 20%

        assertEquals(new BigDecimal("0"), coupon.discountMinorUnitsFor(new BigDecimal("1")));   // 0.2 -> 0
        assertEquals(new BigDecimal("0"), coupon.discountMinorUnitsFor(new BigDecimal("4")));   // 0.8 -> 0
        assertEquals(new BigDecimal("1"), coupon.discountMinorUnitsFor(new BigDecimal("5")));   // 1.0 -> 1
        assertEquals(new BigDecimal("2"), coupon.discountMinorUnitsFor(new BigDecimal("14")));  // 2.8 -> 2
    }

    @Test
    void fixedDiscountRoundingIsAlsoDown() {
        // A fixed amount is exact minor units already, but the same DOWN rule applies to the scale.
        Coupon fixed = new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD));
        assertEquals(new BigDecimal("2000"), fixed.discountMinorUnits());
    }
}
