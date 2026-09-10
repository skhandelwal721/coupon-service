package com.northwind.coupon.chargeback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChargebackMatcherTest {

    private final ChargebackMatcher matcher = new ChargebackMatcher();

    @Test
    void attributesAWorldpayReference() {
        assertEquals(ChargebackMatcher.Acquirer.WORLDPAY, matcher.acquirerOf("wp_4f8a21c7"));
    }

    @Test
    void attributesAnAdyenReference() {
        assertEquals(ChargebackMatcher.Acquirer.ADYEN, matcher.acquirerOf("ad_9b31f7ca"));
    }

    /**
     * An acquirer we have not mapped yet resolves to {@code UNKNOWN} so the nightly batch can
     * finish. This used to throw, which took the whole reconciliation run down with it.
     */
    @Test
    void resolvesAnUnmappedAcquirerToUnknown() {
        assertEquals(ChargebackMatcher.Acquirer.UNKNOWN, matcher.acquirerOf("amex_7c2b91de"));
    }

    @Test
    void resolvesANullReferenceToUnknown() {
        assertEquals(ChargebackMatcher.Acquirer.UNKNOWN, matcher.acquirerOf(null));
    }

    @Test
    void doesNotMatchAPrefixThatOnlyAppearsMidReference() {
        assertEquals(ChargebackMatcher.Acquirer.UNKNOWN, matcher.acquirerOf("xx_wp_4f8a21c7"));
    }
}
