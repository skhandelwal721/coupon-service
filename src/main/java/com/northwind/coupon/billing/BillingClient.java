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
 * {@code POST /v1/charges} and marks it preferred; we have deliberately not migrated. Blocked
 * on COUPON-441.
 *
 * <p>Since COUPON-490 we also send {@code billingPostcode}. billing-service uses it as the VAT
 * place-of-supply input, and a cross-border EUR supply must be taxed in the customer's member
 * state rather than ours.
 *
 * <p><strong>It is sent in the form the storefront collected it in</strong> — {@code "DE-10115"},
 * {@code "EC2A 4BX"} — and is not normalised on the way out. COUPON-490 applied the SEPA
 * structured-address normalisation here, which stripped the separator and left
 * {@code "DE10115"}. billing-service resolves the member state by matching the country prefix
 * ({@code PlaceOfSupply.forPostcode}), did not match, and fell back to its home jurisdiction —
 * so every euro charge was taxed at the UK rate and declared in the wrong member state, with
 * nothing raised anywhere. SEPA normalisation belongs on the settlement instruction, which is
 * the only place the scheme's address restrictions apply. See
 * {@link com.northwind.coupon.sepa.SepaAddressNormaliser}.
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
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency,
                                   String billingPostcode) {
        String url = baseUrl + chargePath.replace("{invoiceId}", invoiceId);
        String postcode = postcodeForCharge(billingPostcode);

        log.info("charging via billing-service invoiceId={} url={} currency={} postcodeSent={}",
                invoiceId, url, currency, postcode != null);

        // Stubbed for the fixture: the real client POSTs
        // { cardNumber, currency, billingPostcode: postcode } to the charge path and
        // deserializes into BillingChargeView with the strict ObjectMapper configured in
        // application.yml.
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

    /**
     * The postcode we put on the charge request: exactly what the storefront collected.
     *
     * <p><strong>Not normalised.</strong> billing-service resolves the VAT member state by
     * matching the country prefix on this string ({@code PlaceOfSupply.forPostcode}), so
     * stripping the separator silently changes which country's tax rate is applied. That is
     * what COUPON-490 did.
     *
     * <p>Package-visible so {@code BillingClientTest} can pin it — the jurisdiction
     * billing-service derives depends on this exact value, which makes it part of our outbound
     * contract with them rather than an implementation detail.
     */
    String postcodeForCharge(String billingPostcode) {
        return billingPostcode;
    }
}
