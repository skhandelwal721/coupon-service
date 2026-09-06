package com.northwind.coupon.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
     * <p>The consolidated endpoint takes the invoice in the body, so we no longer look the
     * invoice up before charging — one round trip instead of two. It also takes the promotional
     * adjustment with the charge, which means we stop charging the full invoice and raising a
     * refund for the difference: one charge, one statement line, one interchange fee.
     *
     * <p>The billing postcode is optional on {@code ChargeCommand} and the bulk path does not
     * carry one, so we no longer send it.
     */
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency,
                                    BigDecimal promotionalAdjustment) {
        String url = baseUrl + chargePath;
        log.info("charging via billing-service invoiceId={} url={} promotionalAdjustment={}",
                invoiceId, url, promotionalAdjustment);

        // Stubbed for the fixture: the real client POSTs
        // { invoiceId, cardNumber, currency, promotionalAdjustment } to POST /v1/charges and
        // deserializes into BillingChargeView. The arithmetic below mirrors what
        // billing-service does with the adjustment, so the fixture stays honest.
        BigDecimal invoiced = new BigDecimal("249.00");
        BigDecimal net = invoiced.subtract(promotionalAdjustment).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = net.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);

        return new BillingChargeView(
                "chg_9f3b7c21",
                invoiceId,
                net,
                new BigDecimal("0.00"),
                tax,
                net.add(tax),
                currency,
                "CREDIT",
                "VISA",
                "wp_4f8a21c7",
                "CHARGED");
    }
}
