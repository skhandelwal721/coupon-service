package com.beaconstone.coupon.billing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.beaconstone.coupon.sepa.SepaAddressNormaliser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COUPON-620. The correlation id travels to billing-service, and its absence changes nothing.
 *
 * <p>The client is stubbed for the fixture, so what is assertable here is that the id reaches the
 * call and that the charge the caller gets back is identical either way. The header name itself
 * is pinned, because the entrypoint reads the header by that constant and this client sends it
 * on by the same one.
 */
class BillingClientCorrelationIdTest {

    private static final String CORRELATION_ID = "ord_4e91c7a2";

    private final BillingClient client = new BillingClient(
            "http://billing-service.prod.internal",
            "/v1/invoices/{invoiceId}/charge",
            new SepaAddressNormaliser(),
            new CardMask());

    private Logger billingClientLogger;
    private ListAppender<ILoggingEvent> captured;
    private Level originalLevel;

    @BeforeEach
    void captureTheChargePathsLogs() {
        billingClientLogger = (Logger) LoggerFactory.getLogger(BillingClient.class);
        originalLevel = billingClientLogger.getLevel();
        captured = new ListAppender<>();
        captured.start();
        billingClientLogger.addAppender(captured);
        billingClientLogger.setLevel(Level.TRACE);
    }

    @AfterEach
    void stopCapturing() {
        billingClientLogger.detachAppender(captured);
        billingClientLogger.setLevel(originalLevel);
        captured.stop();
    }

    @Test
    void theHeaderNameIsTheOneTheEntrypointReads() {
        assertEquals("X-Beacon-Correlation-Id", BillingClient.CORRELATION_ID_HEADER);
    }

    @Test
    void theCorrelationIdReachesTheChargeCall() {
        charge(CORRELATION_ID);

        assertTrue(loggedLines().stream().anyMatch(line -> line.contains(CORRELATION_ID)),
                "the correlation id should reach the charge call: " + loggedLines());
    }

    /** The whole point of it being optional: without one, this is the old call. */
    @Test
    void withoutOneNothingIsPropagatedAndTheChargeIsUnchanged() {
        BillingChargeView withoutId = charge(null);

        assertTrue(loggedLines().stream().anyMatch(line -> line.contains("correlationId=null")),
                "no id should be propagated: " + loggedLines());
        assertEquals(charge(CORRELATION_ID), withoutId, "the charge itself must not depend on it");
    }

    /** The old five-argument signature still compiles and still behaves as it did. */
    @Test
    void theSignatureWithoutACorrelationIdStillWorks() {
        BillingChargeView throughTheOldSignature = client.charge(
                "inv-1001", "4111111111111111", "EUR", "NL-1011AB", new BigDecimal("2000"));

        assertEquals(charge(null), throughTheOldSignature);
        assertTrue(loggedLines().stream().anyMatch(line -> line.contains("correlationId=null")));
    }

    /** A blank id is treated as no id, not as an id that happens to be empty. */
    @Test
    void aBlankCorrelationIdIsNotPropagatedAsAValue() {
        charge("   ");

        assertTrue(loggedLines().stream().anyMatch(line -> line.contains("correlationId=null")),
                "a blank id should be normalised to none: " + loggedLines());
    }

    /** It is the caller's request id, so nothing about the card path may depend on it. */
    @Test
    void theCardIsStillMaskedAndTheFullValueStillDoesNotReachTheLog() {
        charge(CORRELATION_ID);

        for (String line : loggedLines()) {
            assertTrue(!line.contains("4111111111111111"), "the full value reached the log: " + line);
        }
        assertTrue(loggedLines().stream().anyMatch(line -> line.contains("****1111")));
    }

    private BillingChargeView charge(String correlationId) {
        return client.charge("inv-1001", "4111111111111111", "EUR", "NL-1011AB",
                new BigDecimal("2000"), correlationId);
    }

    private List<String> loggedLines() {
        return captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
