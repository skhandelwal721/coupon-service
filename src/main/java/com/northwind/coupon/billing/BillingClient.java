package com.northwind.coupon.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Reads charges from billing-service.
 *
 * <p><strong>Hard dependency.</strong> A redemption is only valid against a charge that
 * actually settled, so we do not complete one without reading the charge back.
 *
 * <p>We now call {@code POST /v1/charges}, which billing-service marks as preferred in their
 * README. It takes the invoice in the body, so we no longer need a separate lookup before
 * charging — one round trip instead of two. Closes COUPON-441.
 */
@Component
public class BillingClient {

    private static final Logger log = LoggerFactory.getLogger(BillingClient.class);

    private final String baseUrl;
    private final String chargePath;

    public BillingClient(@Value("${clients.billing.baseUrl}") String baseUrl,
                         @Value("${clients.billing.chargePath}") String chargePath) {
        this.baseUrl = baseUrl;
        this.chargePath = chargePath;
    }

    /**
     * Charges an invoice through billing-service and returns the charge as we understand it.
     *
     * <p>The consolidated endpoint takes the invoice in the body. We send the invoice, the card
     * and the currency; the billing postcode is optional on {@code ChargeCommand} and the bulk
     * path does not carry one, so we no longer send it.
     */
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency) {
        String url = baseUrl + chargePath;
        log.info("charging via billing-service invoiceId={} url={}", invoiceId, url);

        // Stubbed for the fixture: the real client POSTs
        // { invoiceId, cardNumber, currency } to POST /v1/charges and deserializes into
        // BillingChargeView.
        return new BillingChargeView(
                "chg_9f3b7c21",
                invoiceId,
                new BigDecimal("249.00"),
                new BigDecimal("0.00"),
                new BigDecimal("49.80"),
                new BigDecimal("298.80"),
                currency,
                "CREDIT",
                "VISA",
                "wp_4f8a21c7",
                "CHARGED");
    }
}
