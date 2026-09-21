package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COUPON-615 — the invariant is checked where entries are published.
 *
 * <p>COUPON-613 had {@code discountMinorUnits()} answer zero for an entry with no amount. Zero is
 * a legitimate amount, so that answer was indistinguishable from a real one, and the crash that
 * would have exposed the malformed entry was removed in the same change. These tests pin the check
 * to publication and pin the accessor back to one answer.
 */
class CouponDiscountAccessorTest {

    private static Coupon with(BigDecimal amount) {
        return new Coupon("NW-VISA-10", amount, Set.of(CardNetwork.VISA));
    }

    private static Map<String, Coupon> catalogueOf(Coupon... entries) {
        Map<String, Coupon> m = new LinkedHashMap<>();
        for (Coupon c : entries) {
            m.put(c.code(), c);
        }
        return m;
    }

    // --- the invariant is askable, which is what COUPON-613 lacked ---

    @Test
    void anEntryKnowsWhetherItIsWellFormed() {
        assertTrue(with(new BigDecimal("10.00")).isWellFormed());
        assertTrue(with(BigDecimal.ZERO).isWellFormed(), "a real zero is a recorded amount");
        assertFalse(with(null).isWellFormed());
    }

    // --- publication is where it is enforced ---

    @Test
    void aMalformedEntryCannotBePublished() {
        Map<String, Coupon> catalogue = catalogueOf(with(null));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> CouponRepository.publishable(catalogue));
        assertTrue(thrown.getMessage().contains("NW-VISA-10"),
                "the failure names the entry so it is actionable");
    }

    @Test
    void aWellFormedCatalogueIsPublishedUnchanged() {
        Map<String, Coupon> catalogue = catalogueOf(
                with(new BigDecimal("10.00")),
                new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR, Set.of(CardNetwork.VISA)));

        assertSame(catalogue, CouponRepository.publishable(catalogue));
    }

    @Test
    void everyMalformedEntryIsNamed() {
        Map<String, Coupon> catalogue = catalogueOf(
                with(null),
                new Coupon("BS-EU-20", null, Coupon.EUR, Set.of(CardNetwork.VISA)),
                new Coupon("NW-MC-15", new BigDecimal("15.00"), Set.of(CardNetwork.MASTERCARD)));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> CouponRepository.publishable(catalogue));
        assertTrue(thrown.getMessage().contains("BS-EU-20"));
        assertTrue(thrown.getMessage().contains("NW-VISA-10"));
        assertFalse(thrown.getMessage().contains("NW-MC-15"), "a well-formed entry is not named");
    }

    // --- the accessor has one answer again ---

    @Test
    void theAccessorAnswersForAWellFormedEntryOnly() {
        assertEquals(new BigDecimal("1000"), with(new BigDecimal("10.00")).discountMinorUnits());
        assertEquals(new BigDecimal("2490"), with(new BigDecimal("24.90")).discountMinorUnits());
        assertEquals(new BigDecimal("0"), with(BigDecimal.ZERO).discountMinorUnits());
        assertEquals(new BigDecimal("1009"), with(new BigDecimal("10.099")).discountMinorUnits(),
                "truncation unchanged");
    }

    // --- the validation result this change is asked to state ---

    /**
     * The figure the change record cites: the real catalogue, every entry, in both flag states.
     */
    @Test
    void everyEntryInTheRealCatalogueIsWellFormed() {
        for (boolean nlLaunch : new boolean[]{false, true}) {
            CouponRepository repo = new CouponRepository(nlLaunch);
            for (String code : new String[]{"NW-VISA-10", "NW-SUMMER-25", "NW-MC-15",
                    "NW-SEPA-10", "NW-SEPA-25", "NW-SEPA-15", "BS-EU-20"}) {
                assertTrue(repo.find(code).orElseThrow().isWellFormed(),
                        code + " must be well formed with nlLaunch=" + nlLaunch);
            }
        }
        assertTrue(new CouponRepository(true).find("BS-NL-20").orElseThrow().isWellFormed());
    }
}
