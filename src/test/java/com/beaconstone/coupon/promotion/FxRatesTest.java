package com.beaconstone.coupon.promotion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxRatesTest {

    private final FxRates fxRates = new FxRates();

    @Test
    void convertsSterlingToEuro() {
        assertEquals(new BigDecimal("11.70"),
                fxRates.convert(new BigDecimal("10.00"), "GBP", "EUR"));
    }

    @Test
    void convertsEuroToSterling() {
        assertEquals(new BigDecimal("8.50"),
                fxRates.convert(new BigDecimal("10.00"), "EUR", "GBP"));
    }

    @Test
    void leavesTheAmountAloneWhenTheCurrenciesMatch() {
        assertEquals(new BigDecimal("25.00"),
                fxRates.convert(new BigDecimal("25.00"), "EUR", "EUR"));
    }

    @Test
    void roundsToTwoDecimalPlaces() {
        assertEquals(new BigDecimal("29.25"),
                fxRates.convert(new BigDecimal("25.00"), "GBP", "EUR"));
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
    void reportsWhichCorridorsItCovers() {
        assertTrue(fxRates.hasRateFor("GBP", "EUR"));
        assertTrue(fxRates.hasRateFor("EUR", "GBP"));
        assertTrue(fxRates.hasRateFor("GBP", "GBP"));
        assertFalse(fxRates.hasRateFor("GBP", "CHF"));
    }
}
