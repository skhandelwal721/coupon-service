package com.beaconstone.coupon.redemption;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Result of redeeming a coupon.
 *
 * <p><strong>{@code discount} carries the discount in minor units</strong> — pence for a
 * sterling promotion, cents for a euro one. A &pound;24.90 discount is {@code 2490}.
 *
 * <p>This is the representation SEPA instructions use (ISO 20022 {@code InstdAmt} is expressed
 * in the currency's smallest denomination), and running one settlement pipeline over two
 * representations of the same figure is how reconciliation breaks. The field keeps its name and
 * its {@link BigDecimal} type; {@code discountUnit} states the representation explicitly and
 * {@code settlementCurrency} states the currency, so nothing downstream has to infer either.
 *
 * <p>{@code customerIp} is new for COUPON-491. Financial crime need the origin recorded against
 * the redemption itself, not only in our logs: a chargeback arrives weeks later and the log
 * retention window has often closed by then, so the receipt is the only durable record of where
 * the redemption came from.
 *
 * @param redemptionId       our identifier for the redemption
 * @param couponCode         the coupon that was redeemed
 * @param chargeId           the billing-service charge that settled it
 * @param fundingNetwork     the network whose interchange rebate funds the promotion
 * @param discount           the discount in minor units — see {@code discountUnit}
 * @param discountUnit       MINOR_UNITS
 * @param settlementCurrency GBP or EUR
 * @param customerIp         the origin the redemption came from
 * @param status             REDEEMED
 */
public record RedemptionReceipt(
        String redemptionId,
        String couponCode,
        String chargeId,
        String fundingNetwork,

        /**
         * COUPON-616. Serialized as {@code discountMinorUnits}, not {@code discount}.
         *
         * <p>From 3.0.0 this field carries minor units where 2.4.0 carried major units, and it
         * kept its name and its {@code BigDecimal} type through that change. {@code discountUnit}
         * states the representation, and {@code docs/api/redemption.md} records that a consumer
         * pinned to 2.4.0 does not read it — so such a consumer reads a figure 100x larger than
         * intended, arithmetically valid, with nothing thrown and nothing logged.
         *
         * <p>Naming the unit on the wire makes that loud. A consumer that has not been updated
         * finds no {@code discount} field rather than a wrong number in it. Per the compatibility
         * rule in {@code docs/api/redemption.md}, a change to a field's name is a major bump and
         * has to be announced before it ships.
         *
         * <p>The Java accessor is unchanged, so every in-process caller — {@code PromotionLedger},
         * {@code AttributionExport}, {@code RedemptionAnalyticsClient} — is untouched. This is a
         * wire-format change only.
         *
         * <p>Consumers of this field are listed in
         * {@code docs/api/COUPON-616-affected-services.md}.
         */
        @JsonProperty("discountMinorUnits")
        BigDecimal discount,
        String discountUnit,
        String settlementCurrency,
        String customerIp,
        String status
) {

    /** The only representation we issue. Present so the field is never a bare string literal. */
    public static final String MINOR_UNITS = "MINOR_UNITS";

    /**
     * Back-compatible form, for call sites that predate {@code discountUnit},
     * {@code settlementCurrency} and {@code customerIp}.
     *
     * <p>Retained so both changes stay <strong>additive</strong>: every existing construction
     * keeps compiling, and only callers that actually care about the representation or the
     * origin have to supply the new components. Defaults to sterling, which is what every
     * pre-SEPA call site meant, and to no recorded origin.
     */
    public RedemptionReceipt(String redemptionId, String couponCode, String chargeId,
                             String fundingNetwork, BigDecimal discount, String status) {
        this(redemptionId, couponCode, chargeId, fundingNetwork, discount,
                MINOR_UNITS, "GBP", null, status);
    }
}
