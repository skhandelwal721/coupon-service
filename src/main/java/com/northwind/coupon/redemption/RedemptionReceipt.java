package com.northwind.coupon.redemption;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Result of redeeming a coupon.
 *
 * <h2>COUPON-492 — clearer wire names</h2>
 *
 * <p>Two fields are renamed on the wire while keeping their Java accessors:
 *
 * <ul>
 *   <li>{@code couponCode} is published as <strong>{@code voucherCode}</strong>. The business
 *       has called these vouchers since the loyalty programme launched and "coupon" only ever
 *       survived as an internal word.</li>
 *   <li>{@code fundingNetwork} is published as <strong>{@code network}</strong>. The value was
 *       always just the card network; "funding" described where we read it from, not what it
 *       is.</li>
 * </ul>
 *
 * <p>Java call sites are untouched — the accessors keep their names, so this is a serialization
 * concern only and nothing in this repository has to change.
 *
 * <h2>Status</h2>
 *
 * <p>{@code status} is {@link #PENDING} on the synchronous response now that completion is
 * asynchronous, and {@link #REDEEMED} once the redemption event has been processed. See
 * {@code docs/api/redemption.md}.
 */
public record RedemptionReceipt(
        String redemptionId,

        @JsonProperty("voucherCode")
        String couponCode,

        String chargeId,

        @JsonProperty("network")
        String fundingNetwork,

        BigDecimal discount,
        String status
) {

    /** Accepted, charge taken, completion still in flight. */
    public static final String PENDING = "PENDING";

    /** Terminal success. */
    public static final String REDEEMED = "REDEEMED";
}
