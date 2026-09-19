package com.beaconstone.coupon.billing;

import com.beaconstone.coupon.sepa.SepaAddressNormaliser;
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
 * <p>Since COUPON-530 we send the promotional deduction with the charge, so a discounted order
 * is one card transaction instead of a charge plus a refund.
 *
 * <p>We call {@code POST /v1/invoices/{invoiceId}/charge}. billing-service 4.12 introduces
 * {@code POST /v1/charges} and marks it preferred; we have deliberately not migrated. Blocked
 * on COUPON-441.
 *
 * <p>Since COUPON-490 we also send {@code billingPostcode}. billing-service uses it as the VAT
 * place-of-supply input, and a cross-border EUR supply must be taxed in the customer's member
 * state rather than ours. It is sent in SEPA structured-address form — see
 * {@link SepaAddressNormaliser} — because the same address element goes on to the settlement
 * instruction and the clearing house rejects separators.
 *
 * <p>Since COUPON-491 the card number is masked before it leaves us — see {@link CardMask}.
 * PCI-DSS wants the full PAN in as few places as possible, and the last four is all anything
 * downstream of the charge needs to display or reconcile against.
 */
@Component
public class BillingClient {

    private static final Logger log = LoggerFactory.getLogger(BillingClient.class);

    private final String baseUrl;
    private final String chargePath;
    private final SepaAddressNormaliser addressNormaliser;
    private final CardMask cardMask;

    public BillingClient(@Value("${clients.billing.baseUrl}") String baseUrl,
                         @Value("${clients.billing.chargePath}") String chargePath,
                         SepaAddressNormaliser addressNormaliser,
                         CardMask cardMask) {
        this.baseUrl = baseUrl;
        this.chargePath = chargePath;
        this.addressNormaliser = addressNormaliser;
        this.cardMask = cardMask;
    }

    /**
     * The header the caller's correlation id travels on — COUPON-620.
     *
     * <p>Public and defined once: the entrypoint reads the header by this name and this client
     * sends it on by the same name, so the two cannot drift apart.
     */
    public static final String CORRELATION_ID_HEADER = "X-Beacon-Correlation-Id";

    /**
     * Charges an invoice through billing-service and returns the charge as we understand it.
     *
     * <p>The response is deserialized into {@link BillingChargeView}, which is strict. A
     * response carrying a field our pinned contract version does not declare fails here.
     *
     * <p>Form without a correlation id, retained for call sites that predate COUPON-620 —
     * including the promotions backfill job. Behaves exactly as before.
     */
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency,
                                   String billingPostcode, BigDecimal promotionalAdjustment) {
        return charge(invoiceId, cardNumber, currency, billingPostcode, promotionalAdjustment,
                null);
    }

    /**
     * As above, propagating the caller's correlation id to billing-service — COUPON-620.
     *
     * <p>{@code correlationId} is sent as the {@code X-Beacon-Correlation-Id} <strong>request
     * header</strong>, not on the body. That is deliberate: the charge request body is validated
     * against billing-service's pinned schema, so adding a field to it would be a contract
     * change needing their release. A header is not schema-validated and is ignored by a service
     * that does not read it, which is what makes this safe to ship on our side alone.
     *
     * <p>{@code null} or blank means the caller sent none: the header is omitted and the call is
     * byte-for-byte what it was before.
     */
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency,
                                   String billingPostcode, BigDecimal promotionalAdjustment,
                                   String correlationId) {
        String url = baseUrl + chargePath.replace("{invoiceId}", invoiceId);

        // A blank id is a caller that sent the header with nothing in it. Treat that as no id
        // rather than propagating an empty value, so downstream never has to distinguish the two.
        String propagatedCorrelationId =
                correlationId == null || correlationId.isBlank() ? null : correlationId;

        // SEPA structured-address form. The same element goes on to the settlement
        // instruction, so it has to be clearing-house clean before it leaves us.
        String sepaPostcode = addressNormaliser.normalise(billingPostcode);

        // PCI: the full PAN does not leave this method. billing-service reconciles and displays
        // on the last four, which is what the mask preserves.
        String maskedCardNumber = cardMask.mask(cardNumber);

        log.info("charging via billing-service invoiceId={} url={} currency={} cardNumber={} "
                        + "postcodeSent={} correlationId={}",
                invoiceId, url, currency, maskedCardNumber, sepaPostcode != null,
                propagatedCorrelationId);

        log.info("applying promotional adjustment invoiceId={} adjustment={}",
                invoiceId, promotionalAdjustment);

        // Stubbed for the fixture: the real client POSTs
        // { cardNumber: maskedCardNumber, currency, billingPostcode: sepaPostcode } to the
        // charge path and deserializes into BillingChargeView with the strict ObjectMapper
        // configured in application.yml. promotionalAdjustment goes on the same body, and
        // propagatedCorrelationId goes on the CORRELATION_ID_HEADER header when it is non-null,
        // and the header is omitted entirely when it is null.
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
