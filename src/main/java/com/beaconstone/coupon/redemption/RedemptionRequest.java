package com.beaconstone.coupon.redemption;

import com.beaconstone.coupon.billing.CardMask;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A redemption attempt.
 *
 * <p>{@code billingPostcode} arrived with COUPON-490. billing-service uses it as the VAT
 * place-of-supply input.
 *
 * <p>{@code deviceId}, {@code customerIp} and {@code customerEmail} are new for COUPON-491.
 * They are the inputs to device-and-origin velocity checking — see {@link
 * com.beaconstone.coupon.fraud.DeviceFingerprint}. The device and origin are required, because a
 * velocity check that silently falls back to "unknown" for either is a velocity check that does
 * not run.
 */
public record RedemptionRequest(

        /**
         * The coupon code, in catalogue form.
         *
         * <p>{@code BS-} is the Beacon Stone prefix, new for COUPON-550. {@code NW-} is the
         * Northwind-era prefix every code in the catalogue carried before the rebrand, and it
         * stays accepted: those codes are printed on cards and in live campaigns, so narrowing
         * the pattern to the new prefix would refuse promotions customers already hold.
         */
        @NotBlank(message = "couponCode is required")
        @Pattern(regexp = "^(BS|NW)-[A-Z]{2,4}-\\d{2}$",
                message = "couponCode must look like BS-XXXX-00")
        String couponCode,

        @NotBlank(message = "invoiceId is required")
        String invoiceId,

        @NotBlank(message = "cardNumber is required")
        String cardNumber,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^(GBP|EUR)$", message = "currency must be GBP or EUR")
        String currency,

        /**
         * The customer's billing postcode, in storefront display form.
         *
         * <p>From COUPON-490. billing-service uses it as the VAT place-of-supply input, and
         * a cross-border EUR supply has to be taxed in the customer's member state. Optional so
         * the promotions backfill job, which has no postcode for historic orders, keeps working.
         */
        String billingPostcode,

        /**
         * The storefront's device identifier for this browser or app install.
         *
         * <p>Required. A redemption we cannot attribute to a device cannot be velocity checked
         * against the catalogue-sweep pattern, and an optional field here would mean an attacker
         * opts out of the check by omitting it.
         */
        @NotBlank(message = "deviceId is required")
        String deviceId,

        /**
         * The originating IP, as seen by the storefront edge.
         *
         * <p>Required, for the same reason as {@code deviceId}.
         */
        @NotBlank(message = "customerIp is required")
        String customerIp,

        /**
         * The customer's email.
         *
         * <p>Optional. Financial crime use it to link attempts across devices during an
         * investigation; it is not part of the automated decision.
         */
        @Email(message = "customerEmail must be a valid email address")
        String customerEmail
) {

    /** What replaces the local part of an email. */
    private static final String REDACTED = "***";

    /**
     * Redacted rendering, from COUPON-562.
     *
     * <p>A record's generated {@code toString()} prints every component, so the default form of
     * this one prints a full card number and a full email address. Nothing logs the request
     * object today — the velocity check names the fields it wants — but the default is one
     * {@code log.debug("request={}", request)} away from putting a PAN in a log file, and the
     * framework reaches for {@code toString()} on its own in validation-failure messages.
     *
     * <p>So the card is rendered through the standard display mask and the email keeps only its
     * domain. Everything a responder actually diagnoses from — coupon code, invoice, currency,
     * device, origin — is unchanged, because a redaction that removes the diagnostic value is a
     * redaction someone works around.
     *
     * <p>This changes no log output that exists today. It changes what the default would print
     * if anything ever did.
     */
    @Override
    public String toString() {
        return "RedemptionRequest[couponCode=" + couponCode
                + ", invoiceId=" + invoiceId
                + ", cardNumber=" + CardMask.masked(cardNumber)
                + ", currency=" + currency
                + ", billingPostcode=" + billingPostcode
                + ", deviceId=" + deviceId
                + ", customerIp=" + customerIp
                + ", customerEmail=" + redactedEmail(customerEmail)
                + "]";
    }

    /**
     * The email with its local part removed — {@code shopper@example.com} becomes
     * {@code ***@example.com}. The domain is kept because it is what financial crime pattern
     * abuse on, and it identifies nobody on its own.
     */
    private static String redactedEmail(String email) {
        if (email == null || email.isBlank()) {
            return REDACTED;
        }
        int at = email.lastIndexOf('@');
        return at < 0 ? REDACTED : REDACTED + email.substring(at);
    }
}
