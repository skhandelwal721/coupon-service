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
     * Charges an invoice through billing-service and returns the charge as we understand it.
     *
     * <p>The response is deserialized into {@link BillingChargeView}, which is strict. A
     * response carrying a field our pinned contract version does not declare fails here.
     */
    public BillingChargeView charge(String invoiceId, String cardNumber, String currency,
                                   String billingPostcode, BigDecimal promotionalAdjustment) {
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

        log.info("applying promotional adjustment invoiceId={} adjustment={}",
                invoiceId, promotionalAdjustment);

        // Stubbed for the fixture: the real client POSTs
        // { cardNumber: maskedCardNumber, currency, billingPostcode: sepaPostcode } to the
        // charge path and deserializes into BillingChargeView with the strict ObjectMapper
        // configured in application.yml. promotionalAdjustment goes on the same body.
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
     * COUPON-610 fix: applies a promotional deduction to an already-established charge and
     * returns billing-service's re-settled view of it.
     *
     * <p>A FIXED coupon's deduction is a constant known before the charge, so it rides on the
     * single {@link #charge} call as {@code promotionalAdjustment}. A PERCENTAGE coupon's
     * deduction is a function of the subtotal that only the charge establishes, so it cannot be
     * sent on the first call. Before this method existed the percentage was computed after the
     * charge and booked to the ledger but <strong>never applied to the card</strong>: the
     * customer was charged the full subtotal while the ledger recorded a discount, so every
     * percentage redemption drifted the ledger from the money that actually moved.
     *
     * <p>This is the second leg of the two-phase apply. billing-service exposes
     * {@code POST /v1/charges/{chargeId}/adjustments}; we send {@code discountMinorUnits} and it
     * re-settles the same charge at {@code subtotal - discount}, keeping one statement line and
     * one interchange fee — the single-transaction model COUPON-530 established. The returned
     * view reflects the reduced {@code subtotal} and {@code total}, which is what the caller
     * reconciles the ledger against.
     */
    public BillingChargeView applyPromotionalDiscount(BillingChargeView charge,
                                                      BigDecimal discountMinorUnits) {
        BigDecimal discountMajorUnits = discountMinorUnits
                .movePointLeft(2)
                .setScale(2, java.math.RoundingMode.UNNECESSARY);

        String adjustmentUrl = baseUrl + "/v1/charges/" + charge.chargeId() + "/adjustments";

        log.info("applying promotional discount to settled charge chargeId={} url={} "
                        + "discountMinorUnits={} discountMajorUnits={}",
                charge.chargeId(), adjustmentUrl, discountMinorUnits, discountMajorUnits);

        // Stubbed for the fixture: the real client POSTs { discountMinorUnits } to the
        // adjustments path and deserializes the re-settled charge into BillingChargeView with
        // the same strict ObjectMapper. Here we mirror billing-service's arithmetic: the
        // deduction comes off the subtotal, and total follows as subtotal - discount + tax.
        BigDecimal reducedSubtotal = charge.subtotal().subtract(discountMajorUnits);
        BigDecimal reducedTotal = reducedSubtotal.add(charge.tax());

        return new BillingChargeView(
                charge.chargeId(),
                charge.invoiceId(),
                reducedSubtotal,
                charge.tax(),
                reducedTotal,
                charge.currency(),
                charge.cardType(),
                charge.acquirerReference(),
                charge.status());
    }
}
