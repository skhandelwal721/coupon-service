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

    /** The code has to match the request pattern, or it can never be redeemed. */
    @Test
    void everyCatalogueCodeMatchesTheRequestPattern() {
        for (Coupon coupon : repository.all()) {
            assertTrue(coupon.code().matches("^NW-[A-Z]{2,4}-\\d{2}$"),
                    coupon.code() + " cannot be submitted — RedemptionRequest would reject it");
        }
    }
}
