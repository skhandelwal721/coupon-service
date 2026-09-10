package com.northwind.coupon.analytics;

import com.northwind.coupon.redemption.RedemptionReceipt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Publishes every redemption to the shared Northwind analytics platform, so growth can report
 * on promotion take-up without waiting for the overnight warehouse load.
 *
 * <p>Requested by growth for the Q4 promotion review — see COUPON-461.
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
    public void publish(RedemptionReceipt receipt) {
        String body = "{\"redemptionId\":\"" + receipt.redemptionId()
                + "\",\"couponCode\":\"" + receipt.couponCode()
                + "\",\"fundingNetwork\":\"" + receipt.fundingNetwork()
                + "\",\"discount\":\"" + receipt.discount() + "\"}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "could not publish redemption analytics for " + receipt.redemptionId(), e);
        }
    }
}
