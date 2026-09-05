package com.northwind.coupon.redemption;

import jakarta.validation.constraints.NotBlank;

public record RedemptionRequest(

        @NotBlank(message = "couponCode is required")
        String couponCode,

        @NotBlank(message = "invoiceId is required")
        String invoiceId,

        @NotBlank(message = "cardNumber is required")
        String cardNumber,

        @NotBlank(message = "currency is required")
        String currency
) {
}
