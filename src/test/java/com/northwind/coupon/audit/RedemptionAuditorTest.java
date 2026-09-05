package com.northwind.coupon.audit;

import com.northwind.coupon.billing.BillingChargeView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedemptionAuditorTest {

    private final RedemptionAuditor auditor = new RedemptionAuditor();

    @Test
    void acceptsAChargeThatBalances() {
        assertDoesNotThrow(() -> auditor.requireAccountable(
                charge("249.00", "49.80", "298.80")));
    }

    /**
     * A total carrying an amount the subtotal and tax do not account for. billing-service's
     * published invariant is {@code subtotal + tax == total}; anything else means we cannot
     * reconstruct what we are discounting.
     *
     * <p>249.00 + 1.5% = 252.74 taxable, + 49.80 tax = 302.54 total against a 249.00 subtotal:
     * 3.74 unaccounted for.
     */
    @Test
    void holdsAChargeCarryingAnAmountItCannotAccountFor() {
        RedemptionAuditor.UnaccountableChargeException e = assertThrows(
                RedemptionAuditor.UnaccountableChargeException.class,
                () -> auditor.requireAccountable(charge("249.00", "49.80", "302.54")));

        assertTrue(e.getMessage().contains("does not balance"));
        assertTrue(e.getMessage().contains("3.74"));
    }

    private static BillingChargeView charge(String subtotal, String tax, String total) {
        return new BillingChargeView(
                "chg_9", "inv-1001",
                new BigDecimal(subtotal), new BigDecimal(tax), new BigDecimal(total),
                "GBP", "VISA", "wp_4f8a21c7", "CHARGED");
    }
}
