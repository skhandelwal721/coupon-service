package com.northwind.coupon.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.coupon.redemption.RedemptionReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedemptionEventPublisherTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-09-11T10:14:22Z"), ZoneOffset.UTC);

    private final RedemptionEventPublisher publisher =
            new RedemptionEventPublisher("northwind.coupon.redemption.completed", FIXED);

    @Test
    void buildsTheCompletionEventFromTheReceipt() {
        RedemptionCompletedEvent event = publisher.eventFor(receipt());

        assertEquals(RedemptionCompletedEvent.EVENT_TYPE, event.eventType());
        assertEquals("NW-VISA-10", event.voucherCode());
        assertEquals("chg_9f3b7c21", event.chargeId());
        assertEquals("VISA", event.network());
        assertEquals(new BigDecimal("24.90"), event.discount());
        assertEquals("2026-09-11T10:14:22Z", event.occurredAt());
    }

    /** The completion event is what moves a redemption out of PENDING. */
    @Test
    void publishesTheTerminalStatus() {
        assertEquals(RedemptionReceipt.REDEEMED, publisher.eventFor(receipt()).status());
    }

    @Test
    void stampsAUniqueEventIdPerPublication() {
        assertNotEquals(
                publisher.eventFor(receipt()).eventId(),
                publisher.eventFor(receipt()).eventId());
        assertTrue(publisher.eventFor(receipt()).eventId().startsWith("evt_"));
    }

    @Test
    void publishReturnsThePayloadItEmitted() {
        RedemptionCompletedEvent event = publisher.publish(receipt());

        assertEquals("NW-VISA-10", event.voucherCode());
        assertEquals("VISA", event.network());
    }

    /**
     * The event and the synchronous response serialize with the same names, so a consumer can
     * use one deserializer for both.
     */
    @Test
    void theEventAndTheReceiptUseTheSameWireNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String receiptJson = mapper.writeValueAsString(receipt());
        String eventJson = mapper.writeValueAsString(publisher.eventFor(receipt()));

        assertTrue(receiptJson.contains("\"voucherCode\":\"NW-VISA-10\""), receiptJson);
        assertTrue(receiptJson.contains("\"network\":\"VISA\""), receiptJson);
        assertTrue(eventJson.contains("\"voucherCode\":\"NW-VISA-10\""), eventJson);
        assertTrue(eventJson.contains("\"network\":\"VISA\""), eventJson);
    }

    private static RedemptionReceipt receipt() {
        return new RedemptionReceipt("rdm_4f8a21c7", "NW-VISA-10", "chg_9f3b7c21", "VISA",
                new BigDecimal("24.90"), RedemptionReceipt.PENDING);
    }
}
