package com.northwind.coupon.redemption;

import java.math.BigDecimal;

/**
 * Result of redeeming a coupon.
 *
 * <p><strong>{@code discount} is the absolute amount taken off, in major units of
 * {@code settlementCurrency}.</strong> A &pound;24.90 discount is {@code 24.90}. This is the
 * meaning contract 2.4.0 published and it has not changed.
 *
 * <p>{@code discountMinorUnits} carries the same amount in the currency's smallest denomination
 * — {@code 2490} — which is what SEPA instructions need (ISO 20022 {@code InstdAmt}).
 *
 * <h2>Why there are two fields</h2>
 *
 * <p>COUPON-490 reinterpreted {@code discount} itself as minor units. That was a change of
 * <strong>meaning</strong> on a field that kept its name and its {@link BigDecimal} type, so
 * nothing could detect it: not the compiler, not JSON schema validation, not a consumer's
 * deserializer, and not this repository's own test suite. Every consumer pinned to 2.4.0 went on
 * reading the field on the old basis and was silently wrong by a factor of one hundred.
 *
 * <p>COUPON-495 restores {@code discount} and puts the new representation in a new field.
 * Consumers that need minor units read {@code discountMinorUnits}; consumers that do not are
 * unaffected and require no migration. A new representation gets a new name — it does not
 * redefine an existing one.
 *
 * @param redemptionId       our identifier for the redemption
 * @param couponCode         the coupon that was redeemed
 * @param chargeId           the billing-service charge that settled it
 * @param fundingNetwork     the network whose interchange rebate funds the promotion
 * @param discount           the absolute amount taken off, in major units
 * @param discountMinorUnits the same amount in the currency's smallest denomination
 * @param settlementCurrency GBP or EUR
 * @param status             REDEEMED
 */
public record RedemptionReceipt(
        String redemptionId,
        String couponCode,
        String chargeId,
        String fundingNetwork,
        BigDecimal discount,
        BigDecimal discountMinorUnits,
        String settlementCurrency,
        String status
) {

    /** The settlement currency every pre-SEPA call site meant. */
    public static final String DEFAULT_SETTLEMENT_CURRENCY = "GBP";

    /**
     * Back-compatible form, for call sites that predate the SEPA fields.
     *
     * <p>{@code discount} is taken on its documented basis — major units — and
     * {@code discountMinorUnits} is derived from it, so the two can never disagree.
     */
    public RedemptionReceipt(String redemptionId, String couponCode, String chargeId,
                             String fundingNetwork, BigDecimal discount, String status) {
        this(redemptionId, couponCode, chargeId, fundingNetwork, discount,
                toMinorUnits(discount), DEFAULT_SETTLEMENT_CURRENCY, status);
    }

    /**
     * The minor-unit form of a major-unit amount.
     *
     * <p>Truncates: a fraction of a cent cannot be instructed, and rounding up would instruct
     * more promotional spend than was agreed.
     */
    public static BigDecimal toMinorUnits(BigDecimal majorUnits) {
        return majorUnits == null
                ? null
                : majorUnits.multiply(new BigDecimal("100"))
                        .setScale(0, java.math.RoundingMode.DOWN);
    }
}
