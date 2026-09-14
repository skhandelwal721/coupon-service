package com.northwind.coupon.sepa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SepaAddressNormaliserTest {

    private final SepaAddressNormaliser normaliser = new SepaAddressNormaliser();

    @Test
    void stripsSeparatorsFromAContinentalPostcode() {
        assertEquals("DE10115", normaliser.normalise("DE-10115"));
        assertEquals("FR75001", normaliser.normalise("FR-75001"));
        assertEquals("NL1012", normaliser.normalise("NL-1012"));
    }

    @Test
    void stripsSpacesFromASterlingPostcode() {
        assertEquals("EC2A4BX", normaliser.normalise("EC2A 4BX"));
    }

    @Test
    void upperCases() {
        assertEquals("DE10115", normaliser.normalise("de-10115"));
    }

    @Test
    void leavesAnAlreadyCleanPostcodeAlone() {
        assertEquals("DE10115", normaliser.normalise("DE10115"));
    }

    @Test
    void treatsAnAbsentPostcodeAsNothingToSend() {
        assertNull(normaliser.normalise(null));
        assertNull(normaliser.normalise(""));
        assertNull(normaliser.normalise("   "));
        assertNull(normaliser.normalise("---"));
    }

    /** The country is still the leading two characters, so nothing identifying is lost. */
    @Test
    void preservesTheCountryPrefix() {
        assertEquals("DE", normaliser.normalise("DE-10115").substring(0, 2));
        assertEquals("ES", normaliser.normalise("ES-28001").substring(0, 2));
    }
}
