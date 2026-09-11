package com.northwind.coupon.event;

import java.math.BigDecimal;

/**
 * The {@code northwind.coupon.redemption.completed} payload.
 *
 * <p>Published once per redemption, after the charge has settled and the liability has been
 * booked. This is the completion signal for the asynchronous redemption flow introduced in
 * COUPON-492: the synchronous response is {@code PENDING}, and this event is what moves a
 * redemption to {@code REDEEMED}.
 *
 * <p>Field names match the wire names on {@code RedemptionReceipt} — {@code voucherCode} and
 * {@code network} — so a consumer reading both the synchronous response and the event uses one
 * deserializer for both.
 *
 * @param eventType   the topic name, so a consumer reading a multiplexed stream can route
 * @param eventId     unique per publication, for tracing a single emission
 * @param voucherCode the voucher that was redeemed
 * @param chargeId    the billing-service charge that settled it
 * @param network     the card network whose interchange rebate funds the promotion
 * @param discount    the absolute amount taken off
 * @param status      REDEEMED
 * @param occurredAt  ISO-8601 instant
 */
public record RedemptionCompletedEvent(
        String eventType,
        String eventId,
        String voucherCode,
        String chargeId,
        String network,
        BigDecimal discount,
        String status,
        String occurredAt
) {

    /** The topic this payload is published to. */
    public static final String EVENT_TYPE = "northwind.coupon.redemption.completed";
}
