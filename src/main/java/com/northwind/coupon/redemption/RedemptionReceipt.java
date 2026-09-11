package com.northwind.coupon.redemption;

import java.math.BigDecimal;

/**
 * Result of redeeming a coupon.
 *
 * <p>{@code residencyZone} is new for COUPON-493. Every record has to carry the EU sovereign
 * zone it was processed in, so a residency decision is auditable per record rather than
 * reconstructed from deployment topology.
 *
 * <p>{@code status} gains {@link #REDEEMED_PENDING_RESIDENCY}, for a redemption whose zone
 * could not be resolved from the request. The charge has settled and the discount is booked,
 * but the residency decision has not been made, so the record is marked for review rather than
 * silently processed in whichever region happened to serve the request.
 *
 * @param redemptionId   our identifier for the redemption
 * @param couponCode     the coupon that was redeemed
 * @param chargeId       the billing-service charge that settled it
 * @param fundingNetwork the network whose interchange rebate funds the promotion
 * @param discount       the absolute amount taken off
 * @param residencyZone  the EU sovereign zone this redemption was processed in
 * @param status         REDEEMED, or REDEEMED_PENDING_RESIDENCY
 */
public record RedemptionReceipt(
        String redemptionId,
        String couponCode,
        String chargeId,
        String fundingNetwork,
        BigDecimal discount,
        String residencyZone,
        String status
) {

    /** Terminal success, residency resolved. */
    public static final String REDEEMED = "REDEEMED";

    /**
     * Charge settled and discount booked, but the residency zone could not be resolved from the
     * request. Held for review rather than defaulted silently.
     */
    public static final String REDEEMED_PENDING_RESIDENCY = "REDEEMED_PENDING_RESIDENCY";

    /**
     * Back-compatible form, for call sites that predate {@code residencyZone}.
     *
     * <p>Retained so this change stays <strong>additive</strong>: every existing construction
     * keeps compiling and keeps doing what it did.
     */
    public RedemptionReceipt(String redemptionId, String couponCode, String chargeId,
                             String fundingNetwork, BigDecimal discount, String status) {
        this(redemptionId, couponCode, chargeId, fundingNetwork, discount, null, status);
    }
}
