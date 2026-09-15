package com.northwind.coupon.redemption;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * A redemption attempt.
 *
 * <p>{@code billingPostcode} arrived with COUPON-490. billing-service uses it as the VAT
 * place-of-supply input.
 *
 * <p>{@code deviceId}, {@code customerIp} and {@code customerEmail} arrived with COUPON-491.
 * They are the inputs to device-and-origin velocity checking — see {@link
 * com.northwind.coupon.fraud.DeviceFingerprint}.
 *
 * <p><strong>All three are optional (COUPON-496).</strong> COUPON-491 made the device and origin
 * {@code @NotBlank}, which returned 400 to every consumer pinned to contract 2.4.0 — including
 * `order-service` on the storefront checkout path, so every discounted checkout failed. A
 * required field cannot be introduced additively (ECS-3.2, ECS-3.6).
 *
 * <p>The security intent is met without the validation: an attempt that carries no device or
 * origin is held to a <strong>stricter</strong> limit by {@code VelocityGuard}, so omitting the
 * fields tightens the check rather than disabling it.
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
         * <p>Optional. An attempt without one cannot be checked against the catalogue-sweep
         * pattern, so it is held to {@code fraud.velocity.maxUnattributed} instead — a tighter
         * limit than an attributed attempt gets.
         */
        String deviceId,

        /**
         * The originating IP, as seen by the storefront edge.
         *
         * <p>Optional, and treated the same way as {@code deviceId}.
         */
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
