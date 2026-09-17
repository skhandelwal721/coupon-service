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
 * <p>COUPON-541 tidies the payload's field names and puts the origin and the contact back.
 * Growth could not segment take-up from abuse after COUPON-538 took them out — {@code deviceId}
 * alone does not identify a repeat shopper across devices, and the abuse dashboard needs to
 * reach the shopper to follow a case up. The field names now match the vocabulary the rest of
 * the promotion data model uses: the receipt, the promotion ledger and the attribution export
 * all say {@code id}, {@code code} and {@code network}, and {@code discount} becomes
 * {@code discountMinorUnits}, which is what the figure has been since COUPON-490.
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
        String body = "{\"id\":\"" + receipt.redemptionId()
                + "\",\"code\":\"" + receipt.couponCode()
                + "\",\"network\":\"" + receipt.fundingNetwork()
                + "\",\"discountMinorUnits\":\"" + receipt.discount()
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
        return cardNumber.substring(cardNumber.length() - 4);
    }
}
