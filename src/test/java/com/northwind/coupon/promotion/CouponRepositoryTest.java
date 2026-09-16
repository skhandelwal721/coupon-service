package com.northwind.coupon.promotion;

import com.northwind.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouponRepositoryTest {

    private final CouponRepository repository = new CouponRepository();

    @Test
    void resolvesTheNewEuAcquisitionCoupon() {
        Coupon coupon = repository.find("NW-EU-30").orElseThrow();

        assertEquals("NW-EU-30", coupon.code());
        assertEquals(new BigDecimal("30.00"), coupon.discount());
    }

    @Test
    void theEuAcquisitionCouponSettlesInEuro() {
        Coupon coupon = repository.find("NW-EU-30").orElseThrow();

        assertEquals(Coupon.EUR, coupon.settlementCurrency());
        assertTrue(coupon.isSepaSettled());
    }

    @Test
    void theEuAcquisitionCouponIsVisaFunded() {
        Coupon coupon = repository.find("NW-EU-30").orElseThrow();

        assertTrue(coupon.fundedBy().contains(CardNetwork.VISA));
        assertFalse(coupon.fundedBy().contains(CardNetwork.MASTERCARD));
    }

    @Test
    void convertsToMinorUnitsLikeEveryOtherCatalogueEntry() {
        assertEquals(new BigDecimal("3000"),
                repository.find("NW-EU-30").orElseThrow().discountMinorUnits());
    }

    /** The existing catalogue is untouched. */
    @Test
    void theExistingCatalogueIsUnchanged() {
        assertEquals(new BigDecimal("10.00"),
                repository.find("NW-VISA-10").orElseThrow().discount());
        assertEquals(new BigDecimal("25.00"),
                repository.find("NW-SUMMER-25").orElseThrow().discount());
        assertEquals(new BigDecimal("15.00"),
                repository.find("NW-MC-15").orElseThrow().discount());
        assertEquals(new BigDecimal("25.00"),
                repository.find("NW-SEPA-25").orElseThrow().discount());
    }

    @Test
    void anUnknownCodeResolvesToNothing() {
        assertTrue(repository.find("NW-NOPE-99").isEmpty());
    }

    /**
     * The new code has to match {@code RedemptionRequest}'s pattern, or it can never be
     * redeemed — a catalogue entry the edge validator rejects is a promotion that silently
     * does not exist.
     *
     * <p>Scoped to the entry this change adds. Widening it to the whole catalogue fails today
     * on a pre-existing entry, which is raised separately as COUPON-501 rather than fixed here:
     * this change adds a coupon, and quietly repairing an unrelated one would hide it.
     */
    @Test
    void theNewCodeMatchesTheRequestPattern() {
        assertTrue(repository.find("NW-EU-30").orElseThrow().code()
                        .matches("^NW-[A-Z]{2,4}-\\d{2}$"),
                "NW-EU-30 must be submittable through POST /v1/redemptions");
    }
}
