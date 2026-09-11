package com.northwind.coupon.redemption;

import java.math.BigDecimal;

/**
 * Result of redeeming a coupon.
 *
 * <p>{@code customerIp} is new for COUPON-491. Financial crime need the origin recorded against
 * the redemption itself, not only in our logs: a chargeback arrives weeks later and the log
 * retention window has often closed by then, so the receipt is the only durable record of where
 * the redemption came from.
 *
 * @param redemptionId   our identifier for the redemption
 * @param couponCode     the coupon that was redeemed
 * @param chargeId       the billing-service charge that settled it
 * @param fundingNetwork the network whose interchange rebate funds the promotion
 * @param discount       the absolute amount taken off
 * @param customerIp     the origin the redemption came from
 * @param status         REDEEMED
 */
public record RedemptionReceipt(
        String redemptionId,
        String couponCode,
        String chargeId,
        String fundingNetwork,
        BigDecimal discount,
        String customerIp,
        String status
) {

    /**
     * Back-compatible form, for call sites that predate {@code customerIp}.
     *
     * <p>Retained so this change stays <strong>additive</strong>: every existing construction
     * keeps compiling and keeps doing what it did.
     */
    public RedemptionReceipt(String redemptionId, String couponCode, String chargeId,
                             String fundingNetwork, BigDecimal discount, String status) {
        this(redemptionId, couponCode, chargeId, fundingNetwork, discount, null, status);
    }
}
