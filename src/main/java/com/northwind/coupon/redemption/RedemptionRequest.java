package com.northwind.coupon.redemption;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
         * <p>New for COUPON-490. billing-service uses it as the VAT place-of-supply input, and
         * a cross-border EUR supply has to be taxed in the customer's member state. Optional so
         * the promotions backfill job, which has no postcode for historic orders, keeps working.
         */
        String billingPostcode
) {
}
