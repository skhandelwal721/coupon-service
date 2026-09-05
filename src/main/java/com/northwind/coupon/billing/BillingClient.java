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
 * actually settled, so we do not complete one without reading the charge back. When this call
 * fails we hold the redemption rather than applying an unreconciled discount.
 *
 * <p>We call {@code POST /v1/invoices/{invoiceId}/charge}. billing-service 4.12 introduces
 * {@code POST /v1/charges} and marks it preferred, but we have deliberately not migrated:
 * their {@code docs/runbooks/risk.md} states the pre-charge risk guard is a property of the
 * entrypoint, and the new controller does not call it. Migrating would move our traffic onto
 * an unguarded charge path. Tracked in COUPON-441.
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
     * <p>The response is deserialized into {@link BillingChargeView}, which is strict. A
     * response carrying a field our pinned contract version does not declare fails here.
     */
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency) {
        String url = baseUrl + chargePath.replace("{invoiceId}", invoiceId);
        log.info("charging via billing-service invoiceId={} url={}", invoiceId, url);

        // Stubbed for the fixture: the real client POSTs and deserializes into
        // BillingChargeView with the strict ObjectMapper configured in application.yml.
        return new BillingChargeView(
                "chg_9f3b7c21",
                invoiceId,
                new BigDecimal("249.00"),
                new BigDecimal("49.80"),
                new BigDecimal("298.80"),
                currency,
                "VISA",
                "wp_4f8a21c7",
                "CHARGED");
    }
}
