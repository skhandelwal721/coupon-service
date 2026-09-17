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
 * <p><strong>No monetary amount leaves this service towards the payment processor.</strong> We
 * charge the invoice; we never tell billing-service what to deduct from it. COUPON-530 sent a
 * promotional deduction and COUPON-531 removed it — see
 * {@code docs/api/promotional-adjustment-prerequisites.md} for what has to be true first.
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
     * Charges an invoice through billing-service and returns the charge as we understand it.
     *
     * <p>The response is deserialized into {@link BillingChargeView}, which is strict. A
     * response carrying a field our pinned contract version does not declare fails here.
     */
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency,
                                   String billingPostcode) {
        String url = baseUrl + chargePath.replace("{invoiceId}", invoiceId);

        // SEPA structured-address form. The same element goes on to the settlement
        // instruction, so it has to be clearing-house clean before it leaves us.
        String sepaPostcode = addressNormaliser.normalise(billingPostcode);

        // PCI: the full PAN does not leave this method. billing-service reconciles and displays
        // on the last four, which is what the mask preserves.
        String maskedCardNumber = cardMask.mask(cardNumber);

        log.info("charging via billing-service invoiceId={} url={} currency={} cardNumber={} "
                        + "postcodeSent={}",
                invoiceId, url, currency, maskedCardNumber, sepaPostcode != null);

        // Stubbed for the fixture: the real client POSTs
        // { cardNumber: maskedCardNumber, currency, billingPostcode: sepaPostcode } to the
        // charge path and deserializes into BillingChargeView with the strict ObjectMapper
        // configured in application.yml.
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
