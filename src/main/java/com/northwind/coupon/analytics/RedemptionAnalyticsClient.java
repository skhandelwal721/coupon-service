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
 * <h2>COUPON-496 — three things corrected</h2>
 *
 * <p><strong>1. No personal data leaves here.</strong> COUPON-491 added {@code deviceId},
 * {@code customerIp} and {@code customerEmail} to the event. DPP-4.2 restricts analytics
 * payloads to C1 and C2 fields, and this endpoint is a third-party processor: sending C3 data to
 * it needed a DPA, a Transfer Impact Assessment and DPO approval, none of which exist. The
 * payload is back to promotion attributes only.
 *
 * <p>Growth's abuse dashboard does need to segment take-up from abuse, and it can — from
 * {@code deviceReference}, a non-reversible label derived from the device digest. It groups
 * attempts without identifying anyone.
 *
 * <p><strong>2. Built through Jackson.</strong> The payload was string-concatenated from
 * operator-supplied values, so a quote in one produced malformed JSON the platform dropped
 * silently, and an attacker-controlled field could inject structure.
 *
 * <p><strong>3. Bounded and non-fatal.</strong> There was a connect timeout but no request
 * timeout, so a platform that accepted the connection and stalled held a checkout thread
 * indefinitely (ECS-4.4). And a failure threw out of the controller — after the charge had
 * settled and the discount was booked, so the customer saw checkout fail on an order they had
 * already been charged for. Analytics is not part of the checkout SLO: {@link #publish} now
 * reports failure and never throws.
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
            @Value("${analytics.connectTimeoutMillis:500}") long connectTimeoutMillis,
            @Value("${analytics.requestTimeoutMillis:500}") long requestTimeoutMillis) {
        this(endpoint, connectTimeoutMillis, requestTimeoutMillis, MAPPER);
    }

    RedemptionAnalyticsClient(String endpoint, long connectTimeoutMillis,
                              long requestTimeoutMillis, ObjectMapper mapper) {
        this.endpoint = endpoint;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
    }

    /**
     * Posts the receipt to the analytics platform.
     *
     * @param receipt         the completed redemption
     * @param deviceReference a non-reversible label for grouping attempts, or {@code null}
     * @return {@code true} if the platform accepted it. {@code false} on any failure — the
     *         redemption stands either way, and the caller has nothing to undo.
     */
    public boolean publish(RedemptionReceipt receipt, String deviceReference) {
        String body;
        try {
            body = payloadFor(receipt, deviceReference);
        } catch (Exception e) {
            log.warn("could not serialize redemption analytics redemptionId={}",
                    receipt.redemptionId(), e);
            return false;
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<Void> response =
                    http.send(httpRequest, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() >= 300) {
                log.warn("analytics platform rejected a redemption redemptionId={} status={}",
                        receipt.redemptionId(), response.statusCode());
                return false;
            }

            return true;
        } catch (InterruptedException e) {
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
     * The event body. <strong>C1 and C2 fields only</strong> — DPP-4.2.
     *
     * <p>Built through Jackson rather than string concatenation, because {@code couponCode}
     * reaches us straight off the request body and a quote in one used to produce a payload the
     * platform dropped as malformed.
     */
    String payloadFor(RedemptionReceipt receipt, String deviceReference) throws Exception {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("redemptionId", receipt.redemptionId());
        event.put("couponCode", receipt.couponCode());
        event.put("fundingNetwork", receipt.fundingNetwork());
        event.put("discount", String.valueOf(receipt.discount()));
        event.put("settlementCurrency", receipt.settlementCurrency());

        if (deviceReference != null && !deviceReference.isBlank()) {
            // Grouping only. Derived from a keyed hash, so it segments abuse without
            // identifying a device or a person.
            event.put("deviceReference", deviceReference);
        }

        return mapper.writeValueAsString(event);
    }
}
