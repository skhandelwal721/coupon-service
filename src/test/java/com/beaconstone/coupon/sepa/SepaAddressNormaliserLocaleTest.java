package com.beaconstone.coupon.sepa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * COUPON-621 — the output is a function of the input alone.
 */
class SepaAddressNormaliserLocaleTest {

    private final SepaAddressNormaliser normaliser = new SepaAddressNormaliser();
    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    /**
     * The behaviour this change exists to remove. Under a Turkish default locale the unqualified
     * toUpperCase() maps "i" to a dotted capital outside the accepted set; with the locale pinned
     * the answer is the same on every host.
     */
    @Test
    void theAnswerDoesNotDependOnTheDefaultLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr"));
        String turkish = normaliser.normalise("li-1234");

        Locale.setDefault(Locale.forLanguageTag("en"));
        String english = normaliser.normalise("li-1234");

        assertEquals("LI1234", turkish);
        assertEquals(turkish, english);
    }

    @Test
    void separatorsAndSpacesAreStripped() {
        assertEquals("DE10115", normaliser.normalise("DE-10115"));
        assertEquals("EC2A4BX", normaliser.normalise("EC2A 4BX"));
        assertEquals("NL1012AB", normaliser.normalise("nl 1012 ab"));
    }

    @Test
    void anAlreadyNormalisedValueIsUnchanged() {
        assertEquals("DE10115", normaliser.normalise("DE10115"));
    }

    @Test
    void absentAndEmptyInputsAnswerNull() {
        assertNull(normaliser.normalise(null));
        assertNull(normaliser.normalise("   "));
        assertNull(normaliser.normalise("---"), "nothing alphanumeric survives");
    }

    /** The compiled pattern is reused, so repeated calls agree with single calls. */
    @Test
    void repeatedCallsAgree() {
        for (int i = 0; i < 1000; i++) {
            assertEquals("DE10115", normaliser.normalise("DE-10115"));
        }
    }
}
