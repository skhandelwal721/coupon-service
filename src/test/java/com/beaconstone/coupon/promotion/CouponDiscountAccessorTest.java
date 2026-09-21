package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * COUPON-614 — an entry with no amount recorded cannot be built.
 *
 * <p>COUPON-613 had such an entry answer zero from {@code discountMinorUnits()}. That answer was
 * indistinguishable from a real zero, so a malformed entry read as "nothing off" and nothing
 * anywhere reported a fault. These tests pin the refusal to construction instead, where the entry
 * cannot yet have been acted on.
 */
class CouponDiscountAccessorTest {

    private static Coupon with(BigDecimal amount) {
        return new Coupon("NW-VISA-10", amount, Set.of(CardNetwork.VISA));
    }

    /**
     * The behaviour this change exists to remove: COUPON-613 answered zero here, which a caller
     * could not tell apart from an entry that really is worth nothing.
     */
    @Test
    void anEntryWithNoAmountIsRefusedWhenItIsBuilt() {
        assertThrows(IllegalArgumentException.class, () -> with(null));
    }

    /** A real zero is a legitimate amount and stays legitimate. */
    @Test
    void anAmountOfZeroIsStillAllowed() {
        assertEquals(new BigDecimal("0"), with(BigDecimal.ZERO).discountMinorUnits());
    }

    /** Every other constructor is covered by the same guarantee. */
    @Test
    void theOtherConstructorsRefuseItToo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Coupon("BS-EU-20", null, Coupon.EUR, Set.of(CardNetwork.VISA)));
        assertThrows(IllegalArgumentException.class,
                () -> new Coupon("BS-NL-20", null, Coupon.EUR, Set.of(CardNetwork.VISA), Set.of("NL")));
    }

    /** Present amounts are unaffected, and the truncation is unchanged. */
    @Test
    void presentAmountsAreUnaffected() {
        assertEquals(new BigDecimal("1000"), with(new BigDecimal("10.00")).discountMinorUnits());
        assertEquals(new BigDecimal("2490"), with(new BigDecimal("24.90")).discountMinorUnits());
        assertEquals(new BigDecimal("1009"), with(new BigDecimal("10.099")).discountMinorUnits());
    }

    /** The whole existing catalogue still builds, so the refusal catches faults and not entries. */
    @Test
    void theExistingCatalogueStillBuilds() {
        CouponRepository repo = new CouponRepository();

        assertEquals(new BigDecimal("10.00"), repo.find("NW-VISA-10").orElseThrow().discount());
        assertEquals(new BigDecimal("20.00"), repo.find("BS-EU-20").orElseThrow().discount());
    }
}
