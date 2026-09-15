package com.northwind.coupon.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.coupon.redemption.RedemptionReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedemptionAnalyticsClientTest {

    private static final String IP = "203.0.113.7";
    private static final String DEVICE = "dev-1";
    private static final String EMAIL = "shopper@example.com";
    private static final String PAN = "4111111111111111";

    private final RedemptionAnalyticsClient client = new RedemptionAnalyticsClient(
            "http://127.0.0.1:1/v1/events/redemption", 50, 50, new ObjectMapper());

    // ---------------------------------------------------------------------------------------
    // DPP-4.2 — analytics payloads are restricted to C1 and C2 fields.
    // ---------------------------------------------------------------------------------------

    /**
     * The load-bearing one. COUPON-491 put {@code customerIp}, {@code deviceId} and
     * {@code customerEmail} into this payload, which is a transfer of C3 personal data to a
     * third-party processor with no DPA, no Transfer Impact Assessment and no DPO approval.
     */
    @Test
    void carriesNoPersonalData() throws Exception {
        String payload = client.payloadFor(receipt(), "abc123def456");

        assertFalse(payload.contains(IP), "the originating IP is C3: " + payload);
        assertFalse(payload.contains(DEVICE), "the device identifier is C3: " + payload);
        assertFalse(payload.contains(EMAIL), "the email is C3: " + payload);
        assertFalse(payload.contains("customerIp"));
        assertFalse(payload.contains("customerEmail"));
        assertFalse(payload.contains("deviceId"));
    }

    @Test
    void carriesNoCardholderData() throws Exception {
        String payload = client.payloadFor(receipt(), "abc123def456");

        assertFalse(payload.contains(PAN));
        assertFalse(payload.contains("1111"), "not even the last four: " + payload);
        assertFalse(payload.contains("cardLastFour"));
    }

    /** What growth actually needs to segment abuse: a grouping label, not an identity. */
    @Test
    void carriesANonReversibleDeviceReferenceForGrouping() throws Exception {
        String payload = client.payloadFor(receipt(), "abc123def456");

        assertEquals("abc123def456",
                new ObjectMapper().readTree(payload).get("deviceReference").asText());
    }

    @Test
    void omitsTheDeviceReferenceWhenThereIsNoneToSend() throws Exception {
        assertFalse(client.payloadFor(receipt(), null).contains("deviceReference"));
        assertFalse(client.payloadFor(receipt(), "  ").contains("deviceReference"));
    }

    @Test
    void carriesThePromotionAttributesGrowthAskedFor() throws Exception {
        var event = new ObjectMapper().readTree(client.payloadFor(receipt(), null));

        assertEquals("rdm_1", event.get("redemptionId").asText());
        assertEquals("NW-VISA-10", event.get("couponCode").asText());
        assertEquals("VISA", event.get("fundingNetwork").asText());
        assertEquals("GBP", event.get("settlementCurrency").asText());
    }

    // ---------------------------------------------------------------------------------------
    // Built through Jackson, not concatenated.
    // ---------------------------------------------------------------------------------------

    /**
     * {@code couponCode} reaches us straight off the request body, so it is operator-supplied
     * text. Concatenating it into JSON produced a malformed payload that the platform dropped
     * without telling us, and let an attacker-controlled value inject structure.
     */
    @Test
    void escapesAValueThatWouldHaveBrokenTheJson() throws Exception {
        String payload = client.payloadFor(
                receiptWithCoupon("NW-\"QUOTE\"-10"), null);

        assertTrue(payload.contains("NW-\\\"QUOTE\\\"-10"), payload);
        assertEquals("NW-\"QUOTE\"-10",
                new ObjectMapper().readTree(payload).get("couponCode").asText());
    }

    @Test
    void escapesABackslash() throws Exception {
        String payload = client.payloadFor(receiptWithCoupon("NW-A\\B-10"), null);

        assertEquals("NW-A\\B-10",
                new ObjectMapper().readTree(payload).get("couponCode").asText());
    }

    // ---------------------------------------------------------------------------------------
    // ECS-4.4 and availability.
    // ---------------------------------------------------------------------------------------

    /**
     * This runs after the charge has settled and the discount has been booked, so a publish
     * failure has nothing to abort. It must report, not throw, or a customer sees checkout fail
     * on an order they have already been charged for.
     */
    @Test
    void reportsFailureRatherThanThrowingWhenThePlatformIsUnreachable() {
        assertFalse(client.publish(receipt(), "abc123def456"),
                "an unreachable platform is a false return, never an exception");
    }

    @Test
    void reportsFailureRatherThanThrowingOnAVeryShortTimeout() {
        RedemptionAnalyticsClient impatient = new RedemptionAnalyticsClient(
                "http://127.0.0.1:1/v1/events/redemption", 1, 1, new ObjectMapper());

        assertFalse(impatient.publish(receipt(), null));
    }

    private static RedemptionReceipt receipt() {
        return receiptWithCoupon("NW-VISA-10");
    }

    private static RedemptionReceipt receiptWithCoupon(String couponCode) {
        return new RedemptionReceipt("rdm_1", couponCode, "chg_1", "VISA",
                new BigDecimal("1000"), RedemptionReceipt.MINOR_UNITS, "GBP", IP, "REDEEMED");
    }
}
