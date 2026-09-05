package com.northwind.coupon.chargeback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargebackMatcherTest {

    private final ChargebackMatcher matcher = new ChargebackMatcher();

    @Test
    void attributesAWorldpayReference() {
        assertEquals(ChargebackMatcher.Acquirer.WORLDPAY, matcher.acquirerOf("wp_4f8a21c7"));
    }

    /**
     * Guard rail for a second acquirer.
     *
     * <p>We derive the acquirer from the reference prefix, so a reference from an acquirer we
     * do not know cannot be attributed and the coupon liability is never reversed. If
     * billing-service adds an acquirer, this is where we find out — and the fix is a prefix
     * mapping here, not a relaxation of this test.
     */
    @Test
    void refusesAReferenceFromAnAcquirerItCannotAttribute() {
        ChargebackMatcher.UnattributableChargebackException e = assertThrows(
                ChargebackMatcher.UnattributableChargebackException.class,
                () -> matcher.acquirerOf("amex_7c2b91de"));

        assertTrue(e.getMessage().contains("amex_7c2b91de"));
        assertTrue(e.getMessage().contains("cannot be reversed"));
    }
}
