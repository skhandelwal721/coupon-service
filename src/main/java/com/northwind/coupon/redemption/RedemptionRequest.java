package com.northwind.coupon.redemption;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A redemption attempt.
 *
 * <p>{@code deviceId}, {@code customerIp} and {@code customerEmail} are new for COUPON-491.
 * They are the inputs to device-and-origin velocity checking — see {@link
 * com.northwind.coupon.fraud.DeviceFingerprint}. The device and origin are required, because a
 * velocity check that silently falls back to "unknown" for either is a velocity check that does
 * not run.
 */
public record RedemptionRequest(

        @NotBlank(message = "couponCode is required")
        @Pattern(regexp = "^NW-[A-Z]{2,4}-\\d{2}$",
                message = "couponCode must look like NW-XXXX-00")
        String couponCode,

        @NotBlank(message = "invoiceId is required")
        String invoiceId,

        @NotBlank(message = "cardNumber is required")
        String cardNumber,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^(GBP|EUR)$", message = "currency must be GBP or EUR")
        String currency,

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
}
