package com.beaconstone.coupon.payments;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PAY-8100 — AMEX as a European payment option.
 *
 * <p>These cover the failure modes we have been bitten by before: offering a network we cannot
 * honour (so a customer is charged and then refused), and letting a region-scoped option pass
 * silently when the region is absent.
 */
class AmexEuropeEligibilityTest {

    private final AmexEuropeEligibility enabled = new AmexEuropeEligibility(true);
    private final AmexEuropeEligibility disabled = new AmexEuropeEligibility(false);

    @Test
    void amexIsOfferedInEuropeanMarketsWhenEnabled() {
        assertTrue(enabled.isOffered(CardNetwork.AMEX, "NL"));
        assertTrue(enabled.isOffered(CardNetwork.AMEX, "de"), "match is case-insensitive");
        assertTrue(enabled.isOffered(CardNetwork.AMEX, "FR"));
    }

    @Test
    void amexIsNotOfferedOutsideEurope() {
        assertFalse(enabled.isOffered(CardNetwork.AMEX, "US"));
        assertFalse(enabled.isOffered(CardNetwork.AMEX, "JP"));
    }

    /** A region-scoped option with no region must not silently pass. */
    @Test
    void amexIsNotOfferedWithNoCountry() {
        assertFalse(enabled.isOffered(CardNetwork.AMEX, null));
        assertFalse(enabled.isOffered(CardNetwork.AMEX, ""));
    }

    @Test
    void amexIsNeverOfferedWhenTheFlagIsOff() {
        assertFalse(disabled.isOffered(CardNetwork.AMEX, "NL"),
                "AMEX must not be offered anywhere while payments.amexEurope.enabled is false");
    }

    /** This gate governs only AMEX; other networks are unaffected in any region. */
    @Test
    void otherNetworksAreUnaffected() {
        assertTrue(disabled.isOffered(CardNetwork.VISA, "US"));
        assertTrue(disabled.isOffered(CardNetwork.MASTERCARD, null));
    }

    @Test
    void requireOfferedRefusesBeforeAnyCharge() {
        // The refusal is a thrown exception the caller acts on before calling billing-service.
        assertThrows(AmexEuropeEligibility.AmexNotOfferedException.class,
                () -> enabled.requireOffered(CardNetwork.AMEX, "US"));
        assertDoesNotThrow(() -> enabled.requireOffered(CardNetwork.AMEX, "NL"));
    }
}
