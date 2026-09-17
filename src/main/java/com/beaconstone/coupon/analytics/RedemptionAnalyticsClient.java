package com.beaconstone.coupon.analytics;

import com.beaconstone.coupon.redemption.RedemptionReceipt;
import com.beaconstone.coupon.redemption.RedemptionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Publishes every redemption to the shared Beacon Stone analytics platform, so growth can report
 * on promotion take-up without waiting for the overnight warehouse load.
 *
 * <p>Requested by growth for the Q4 promotion review — see COUPON-461.
 *
 * <p>COUPON-491 added the fraud context to the event, on the argument that growth's abuse
 * dashboard is built on the analytics platform rather than in here.
 *
 * <p><strong>COUPON-538 removes the direct identifiers again.</strong> The origin
 * ({@code customerIp}) and the contact ({@code customerEmail}) were leaving the service to a
 * shared analytics platform with its own retention, its own access list and no entry for them in
 * our record of processing. Under data minimisation they do not belong in a reporting feed:
 * {@code deviceId} and {@code cardLastFour} are enough to segment take-up from abuse, and the
 * fraud path keeps the origin and the contact where they are actually needed — the velocity and
 * device checks, and the redemption receipt.
 *
 * <p><strong>Do not add direct identifiers back to this payload.</strong> Anything that needs a
 * person, rather than a pattern, joins to the receipt on the redemption identifier instead.
 */
@Component
public class RedemptionAnalyticsClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final String endpoint;

    public RedemptionAnalyticsClient(
            @Value("${analytics.redemptionEndpoint}") String endpoint) {
        this.endpoint = endpoint;
    }

    /** Posts the receipt to the analytics platform. */
    public void publish(RedemptionReceipt receipt, RedemptionRequest request) {
        String body = "{\"redemptionId\":\"" + receipt.redemptionId()
                + "\",\"couponCode\":\"" + receipt.couponCode()
                + "\",\"fundingNetwork\":\"" + receipt.fundingNetwork()
                + "\",\"discount\":\"" + receipt.discount()
                + "\",\"deviceId\":\"" + request.deviceId()
                + "\",\"cardLastFour\":\"" + lastFour(request.cardNumber()) + "\"}";

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            http.send(httpRequest, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "could not publish redemption analytics for " + receipt.redemptionId(), e);
        }
    }

    private static String lastFour(String cardNumber) {
        return cardNumber == null || cardNumber.length() < 4
                ? "unknown"
                : cardNumber.substring(cardNumber.length() - 4);
    }
}
