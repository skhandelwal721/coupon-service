package com.northwind.coupon.redemption;

import java.math.BigDecimal;

/**
 * Result of redeeming a coupon.
 *
 * <p>{@code discount} carries the <strong>percentage</strong> the promotion takes off, which is
 * what the coupon codes have always described — {@code NW-VISA-10} is ten percent off. The
 * field previously held the same figure interpreted as an absolute amount, which was only ever
 * correct by coincidence on orders near 100.00.
 *
 * <p>{@code discountBasis} states the basis explicitly, and {@code chargedAmount} carries what
 * the customer was actually charged, so nothing has to be recomputed downstream.
 *
 * @param redemptionId   our identifier for the redemption
 * @param couponCode     the coupon that was redeemed
 * @param chargeId       the billing-service charge that settled it
 * @param fundingNetwork the network whose interchange rebate funds the promotion
 * @param discount       the percentage taken off — see {@code discountBasis}
 * @param discountBasis  PERCENT
 * @param chargedAmount  what the customer was actually charged
 * @param status         REDEEMED, or REDEEMED_PARTIAL on the bulk path
 */
public record RedemptionReceipt(
        String redemptionId,
        String couponCode,
        String chargeId,
        String fundingNetwork,
        BigDecimal discount,
        String discountBasis,
        BigDecimal chargedAmount,
        String status
) {

    /** The only basis we issue. Present so the field is never a bare string literal. */
    public static final String PERCENT = "PERCENT";

    /**
     * Back-compatible form, for call sites that predate {@code discountBasis}.
     *
     * <p>Retained so this change stays <strong>additive</strong>: every existing construction
     * keeps compiling and keeps doing what it did, and only callers that actually care about
     * the basis have to supply the two new components.
     *
     * <p>{@code chargedAmount} is left null here, because a call site written before the field
     * existed has no way to know it.
     */
    public RedemptionReceipt(String redemptionId, String couponCode, String chargeId,
                             String fundingNetwork, BigDecimal discount, String status) {
        this(redemptionId, couponCode, chargeId, fundingNetwork, discount, PERCENT, null, status);
    }
}
