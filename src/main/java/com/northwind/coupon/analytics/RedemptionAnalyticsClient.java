package com.northwind.coupon.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.coupon.redemption.RedemptionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes every redemption to the shared Northwind analytics platform, so growth can report
 * on promotion take-up without waiting for the overnight warehouse load.
 *
 * <p>Requested by growth for the Q4 promotion review — see COUPON-461.
 *
 * <p><strong>Best-effort, by design.</strong> This is called after the charge has settled and
 * the discount has been booked, so the redemption is already complete and irreversible by the
 * time we get here. A failure to publish must therefore never fail the redemption: the customer
 * would see checkout fail on an order that has already been charged, and
 * {@code docs/runbooks/redemption.md} is explicit that a taken charge is not something a
 * revert gives back. {@link #publish} reports success as a return value and never throws.
 *
 * <p>Analytics availability is not part of the checkout SLO. If this starts failing, redemptions
 * keep completing and the warehouse load reconciles the gap overnight.
 */
@Component
public class RedemptionAnalyticsClient {

    private static final Logger log = LoggerFactory.getLogger(RedemptionAnalyticsClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final Duration requestTimeout;

    @Autowired
    public RedemptionAnalyticsClient(
            @Value("${analytics.redemptionEndpoint}") String endpoint,
            @Value("${analytics.requestTimeoutMillis:500}") long requestTimeoutMillis) {
        this(endpoint, requestTimeoutMillis, MAPPER);
    }

    RedemptionAnalyticsClient(String endpoint, long requestTimeoutMillis, ObjectMapper mapper) {
        this.endpoint = endpoint;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    /**
     * Posts the receipt to the analytics platform.
     *
     * @return {@code true} if the platform accepted it. {@code false} on any failure — the
     *         redemption stands either way, and the caller has nothing to undo.
     */
    public boolean publish(RedemptionReceipt receipt) {
        String body;
        try {
            body = payloadFor(receipt);
        } catch (Exception e) {
            // A receipt we cannot even serialize is a bug on our side, not an outage. Log it
            // with the identifier so it can be found, and let the redemption stand.
            log.warn("could not serialize redemption analytics redemptionId={}",
                    receipt.redemptionId(), e);
            return false;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() >= 300) {
                log.warn("analytics platform rejected a redemption redemptionId={} status={}",
                        receipt.redemptionId(), response.statusCode());
                return false;
            }

            log.debug("published redemption analytics redemptionId={}", receipt.redemptionId());
            return true;
        } catch (InterruptedException e) {
            // Never swallow an interrupt — restore the flag so a shutdown still unwinds.
            Thread.currentThread().interrupt();
            log.warn("interrupted publishing redemption analytics redemptionId={}",
                    receipt.redemptionId());
            return false;
        } catch (Exception e) {
            log.warn("could not publish redemption analytics redemptionId={}",
                    receipt.redemptionId(), e);
            return false;
        }
    }

    /**
     * The event body.
     *
     * <p>Built through Jackson rather than string concatenation. {@code couponCode} is
     * operator-supplied and reaches us straight off the request, so a quote or a backslash in
     * one used to produce a payload the platform silently dropped as malformed.
     *
     * <p>{@code discount} stays a JSON string so the wire format is unchanged from COUPON-461 —
     * the platform's schema declares it as a string and a numeric literal would be rejected.
     */
    String payloadFor(RedemptionReceipt receipt) throws Exception {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("redemptionId", receipt.redemptionId());
        event.put("couponCode", receipt.couponCode());
        event.put("fundingNetwork", receipt.fundingNetwork());
        event.put("discount", String.valueOf(receipt.discount()));

        return mapper.writeValueAsString(event);
    }
}
