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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the PCI property of the charge path: the full PAN does not reach the log.
 *
 * <p>{@link BillingClient} masks the card before it logs — that is the control COUPON-491 added,
 * and {@link CardMask} is tested in its own right. What was never tested is the property that
 * actually matters, at the level where a leak would happen: that <em>no</em> line this class
 * emits carries the full value. A future log statement added to this method is exactly how that
 * control gets lost, and nothing would have failed.
 *
 * <p>Captures the class's own logger at {@code TRACE}, so a leak at any level is caught rather
 * than only at whatever level the environment happens to be configured for.
 *
 * <p>Test-only. No production behaviour is changed by this class.
 */
class BillingClientPanLoggingTest {

    private static final String PAN = "4111111111111111";
    private static final String POSTCODE = "GB-EC2A4BX";

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
    void theFullPanNeverReachesTheLog() {
        charge();

        for (String line : loggedLines()) {
            assertFalse(line.contains(PAN), "the full PAN reached the log: " + line);
        }
    }

    @Test
    void theLastFourIsLoggedSoAChargeCanStillBeTraced() {
        charge();

        assertTrue(loggedLines().stream().anyMatch(line -> line.contains("****1111")),
                "the masked card should be logged, or a charge cannot be traced: " + loggedLines());
    }

    /**
     * The postcode is a VAT input, not something to log. The charge line records
     * <em>whether</em> one was sent, not what it was — in either its storefront or its SEPA
     * form.
     */
    @Test
    void theBillingPostcodeIsNotLoggedEither() {
        charge();

        for (String line : loggedLines()) {
            assertFalse(line.contains(POSTCODE), "the postcode reached the log: " + line);
            assertFalse(line.contains("GBEC2A4BX"), "the SEPA postcode reached the log: " + line);
        }
    }

    /** Nothing is asserted about a leak if nothing was captured. */
    @Test
    void theChargePathDoesLogSomething() {
        charge();

        assertFalse(loggedLines().isEmpty(), "captured no log output, so the checks prove nothing");
    }

    private void charge() {
        client.charge("inv-1001", PAN, "GBP", POSTCODE, new BigDecimal("2490"));
    }

    private List<String> loggedLines() {
        return captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
