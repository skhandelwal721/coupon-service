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
        String currency
) {
}
