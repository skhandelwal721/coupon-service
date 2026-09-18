package com.beaconstone.coupon.redemption;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedemptionRequestTest {

    private static final String PAN = "4111111111111111";
    private static final String EMAIL = "shopper@example.com";

    /** The property this change exists for. */
    @Test
    void toStringCarriesNeitherTheCardNumberNorTheEmail() {
        String rendered = request(PAN, EMAIL).toString();

        assertFalse(rendered.contains(PAN), "the full PAN must not appear: " + rendered);
        assertFalse(rendered.contains("shopper@"), "the email local part must not appear: " + rendered);
    }

    @Test
    void toStringKeepsTheCardsLastFourAndTheEmailDomain() {
        String rendered = request(PAN, EMAIL).toString();

        assertTrue(rendered.contains("****1111"));
        assertTrue(rendered.contains("***@example.com"));
    }

    /** A redaction that removes the diagnostic value is a redaction someone works around. */
    @Test
    void toStringKeepsWhatAResponderDiagnosesFrom() {
        String rendered = request(PAN, EMAIL).toString();

        assertTrue(rendered.contains("NW-VISA-10"));
        assertTrue(rendered.contains("inv-1001"));
        assertTrue(rendered.contains("GBP"));
        assertTrue(rendered.contains("dev-1"));
        assertTrue(rendered.contains("203.0.113.7"));
    }

    @Test
    void toStringIsNullSafe() {
        String rendered = request(null, null).toString();

        assertTrue(rendered.contains("cardNumber=****"));
        assertTrue(rendered.contains("customerEmail=***"));
    }

    @Test
    void anEmailWithNoAtSignIsRedactedEntirely() {
        String rendered = request(PAN, "not-an-email").toString();

        assertFalse(rendered.contains("not-an-email"));
        assertTrue(rendered.contains("customerEmail=***"));
    }

    private static RedemptionRequest request(String cardNumber, String customerEmail) {
        return new RedemptionRequest("NW-VISA-10", "inv-1001", cardNumber, "GBP",
                "GB-EC2A4BX", "dev-1", "203.0.113.7", customerEmail);
    }
}
