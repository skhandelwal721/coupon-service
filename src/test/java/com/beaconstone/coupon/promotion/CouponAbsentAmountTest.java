package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * COUPON-620 — the one input {@code discountMinorUnits()} was not total for.
 *
 * <p>Precedent: ITS-6417 and its post-incident review, whose standing action is that an accessor
 * answer for every input it can be handed.
 */
class CouponAbsentAmountTest {

    private static Coupon with(BigDecimal amount) {
        return new Coupon("NW-VISA-10", amount, Set.of(CardNetwork.VISA));
    }

    /** Previously a NullPointerException out of multiply(). */
    @Test
    void anAbsentAmountAnswersZero() {
        assertEquals(BigDecimal.ZERO, with(null).discountMinorUnits());
    }

    @Test
    void bothBranchesAnswerAtTheSameScale() {
        assertEquals(0, with(null).discountMinorUnits().scale());
        assertEquals(0, with(new BigDecimal("10.00")).discountMinorUnits().scale());
    }

    /** The guard does not disturb the arithmetic it guards. */
    @Test
    void presentAmountsAreUnaffected() {
        assertEquals(new BigDecimal("1000"), with(new BigDecimal("10.00")).discountMinorUnits());
        assertEquals(new BigDecimal("2490"), with(new BigDecimal("24.90")).discountMinorUnits());
        assertEquals(new BigDecimal("0"), with(BigDecimal.ZERO).discountMinorUnits());
    }

    @Test
    void truncationIsUnchanged() {
        assertEquals(new BigDecimal("1009"), with(new BigDecimal("10.099")).discountMinorUnits());
    }

    @Test
    void theExistingCatalogueIsUnaffected() {
        CouponRepository repo = new CouponRepository();

        assertEquals(new BigDecimal("1000"), repo.find("NW-VISA-10").orElseThrow().discountMinorUnits());
        assertEquals(new BigDecimal("2000"), repo.find("BS-EU-20").orElseThrow().discountMinorUnits());
    }
}
