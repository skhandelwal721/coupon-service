package com.northwind.coupon.promotion;

import com.northwind.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouponTest {

    @Test
    void convertsASterlingDiscountToPence() {
        assertEquals(new BigDecimal("1000"), coupon("10.00", Coupon.GBP).discountMinorUnits());
        assertEquals(new BigDecimal("2490"), coupon("24.90", Coupon.GBP).discountMinorUnits());
    }

    @Test
    void convertsAEuroDiscountToCents() {
        assertEquals(new BigDecimal("2500"), coupon("25.00", Coupon.EUR).discountMinorUnits());
    }

    /** A fraction of a cent cannot be instructed, and rounding up over-instructs promo spend. */
    @Test
    void truncatesRatherThanRoundingUp() {
        assertEquals(new BigDecimal("1249"), coupon("12.499", Coupon.EUR).discountMinorUnits());
        assertEquals(new BigDecimal("1250"), coupon("12.501", Coupon.EUR).discountMinorUnits());
    }

    @Test
    void defaultsToSterlingThroughTheBackCompatibleConstructor() {
        Coupon legacy = new Coupon("NW-VISA-10", new BigDecimal("10.00"), Set.of(CardNetwork.VISA));

        assertEquals(Coupon.GBP, legacy.settlementCurrency());
        assertFalse(legacy.isSepaSettled());
    }

    @Test
    void marksTheEuroCatalogueAsSepaSettled() {
        assertTrue(coupon("25.00", Coupon.EUR).isSepaSettled());
    }

    @Test
    void theSepaCatalogueIsRegisteredInEuro() {
        CouponRepository repository = new CouponRepository();

        assertTrue(repository.find("NW-SEPA-25").orElseThrow().isSepaSettled());
        assertFalse(repository.find("NW-SUMMER-25").orElseThrow().isSepaSettled());
    }

    private static Coupon coupon(String discount, String currency) {
        return new Coupon("NW-TEST-10", new BigDecimal(discount), currency,
                Set.of(CardNetwork.VISA));
    }
}
