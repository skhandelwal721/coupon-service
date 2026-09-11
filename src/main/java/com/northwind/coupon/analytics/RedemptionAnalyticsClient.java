package com.northwind.coupon.analytics;

import com.northwind.coupon.redemption.RedemptionReceipt;
import com.northwind.coupon.redemption.RedemptionRequest;
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
 *
 * <p>COUPON-491 adds the fraud context to the event. Growth's abuse dashboard is built on the
 * analytics platform rather than in here, so the device, origin and contact have to reach it or
 * the dashboard cannot segment take-up from abuse.
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
                + "\",\"customerIp\":\"" + request.customerIp()
                + "\",\"customerEmail\":\"" + request.customerEmail()
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
