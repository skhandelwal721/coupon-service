package com.northwind.coupon.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.coupon.redemption.RedemptionReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedemptionAnalyticsClientTest {

    private final RedemptionAnalyticsClient client = new RedemptionAnalyticsClient(
            "http://127.0.0.1:1/v1/events/redemption", 50, new ObjectMapper());

    @Test
    void buildsTheEventInTheOrderThePlatformExpects() throws Exception {
        String payload = client.payloadFor(receipt("NW-VISA-10", "24.90"));

        assertEquals("{\"redemptionId\":\"rdm_1\",\"couponCode\":\"NW-VISA-10\","
                + "\"fundingNetwork\":\"VISA\",\"discount\":\"24.90\"}", payload);
    }

    /**
     * {@code couponCode} reaches us straight off the request body, so it is operator-supplied
     * text. Concatenating it into JSON produced a malformed payload that the platform dropped
     * without telling us — the event simply never arrived.
     */
    @Test
    void escapesACouponCodeThatWouldHaveBrokenTheJson() throws Exception {
        String payload = client.payloadFor(receipt("NW-\"QUOTE\"-10", "10.00"));

        assertTrue(payload.contains("NW-\\\"QUOTE\\\"-10"),
                "the quote has to be escaped, not emitted raw: " + payload);

        // Proof it is still parseable, which is the property that actually matters.
        assertEquals("NW-\"QUOTE\"-10",
                new ObjectMapper().readTree(payload).get("couponCode").asText());
    }

    @Test
    void escapesABackslashInACouponCode() throws Exception {
        String payload = client.payloadFor(receipt("NW-A\\B-10", "10.00"));

        assertEquals("NW-A\\B-10",
                new ObjectMapper().readTree(payload).get("couponCode").asText());
    }

    /** The platform's schema declares {@code discount} as a string. Keep it one. */
    @Test
    void keepsTheDiscountAsAStringSoTheWireFormatIsUnchanged() throws Exception {
        String payload = client.payloadFor(receipt("NW-VISA-10", "24.90"));

        assertTrue(new ObjectMapper().readTree(payload).get("discount").isTextual());
    }

    /**
     * The point of the change. This runs after the charge has settled and the discount has been
     * booked, so a publish failure has nothing to abort — it must report, not throw, or a
     * customer sees checkout fail on an order they have already been charged for.
     */
    @Test
    void reportsFailureRatherThanThrowingWhenThePlatformIsUnreachable() {
        assertFalse(client.publish(receipt("NW-VISA-10", "24.90")),
                "an unreachable platform is a false return, never an exception");
    }

    @Test
    void reportsFailureRatherThanThrowingOnAMalformedEndpoint() {
        RedemptionAnalyticsClient broken = new RedemptionAnalyticsClient(
                "http://127.0.0.1:1/v1/events/redemption", 1, new ObjectMapper());

        assertFalse(broken.publish(receipt("NW-VISA-10", "24.90")));
    }

    private static RedemptionReceipt receipt(String couponCode, String discount) {
        return new RedemptionReceipt("rdm_1", couponCode, "chg_1", "VISA",
                new BigDecimal(discount), "REDEEMED");
    }
}
