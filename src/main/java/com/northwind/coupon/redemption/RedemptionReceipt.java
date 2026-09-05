package com.northwind.coupon.redemption;

import java.math.BigDecimal;

public record RedemptionReceipt(
        String redemptionId,
        String couponCode,
        String chargeId,
        String fundingNetwork,
        BigDecimal discount,
        String status
) {
}
