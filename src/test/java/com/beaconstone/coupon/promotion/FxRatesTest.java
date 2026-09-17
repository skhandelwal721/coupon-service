package com.beaconstone.coupon.promotion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxRatesTest {

    private final FxRates fxRates = new FxRates("GBP:EUR=1.17,EUR:GBP=0.85");

    @Test
    void convertsSterlingToEuro() {
        assertEquals(0, new BigDecimal("11.70")
                .compareTo(fxRates.convert(new BigDecimal("10.00"), "GBP", "EUR")));
    }

    @Test
    void convertsEuroToSterling() {
        assertEquals(0, new BigDecimal("8.50")
                .compareTo(fxRates.convert(new BigDecimal("10.00"), "EUR", "GBP")));
    }

    @Test
    void leavesTheAmountAloneWhenTheCurrenciesMatch() {
        assertEquals(new BigDecimal("25.00"),
                fxRates.convert(new BigDecimal("25.00"), "EUR", "EUR"));
    }

    @Test
    void leavesTheAmountAloneWhenWeHoldNoRateForTheCorridor() {
        assertEquals(new BigDecimal("10.00"),
                fxRates.convert(new BigDecimal("10.00"), "GBP", "CHF"));
    }

    @Test
    void handlesAnAbsentAmount() {
        assertNull(fxRates.convert(null, "GBP", "EUR"));
    }

    @Test
    void readsTheCorridorsFromConfiguration() {
        assertTrue(fxRates.hasRateFor("GBP", "EUR"));
        assertTrue(fxRates.hasRateFor("EUR", "GBP"));
        assertTrue(fxRates.hasRateFor("GBP", "GBP"));
        assertFalse(fxRates.hasRateFor("GBP", "CHF"));
    }

    @Test
    void aDifferentConfigurationGivesDifferentCorridors() {
        FxRates other = new FxRates("GBP:CHF=1.12");

        assertTrue(other.hasRateFor("GBP", "CHF"));
        assertFalse(other.hasRateFor("GBP", "EUR"));
    }
}
