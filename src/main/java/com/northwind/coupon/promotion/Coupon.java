package com.northwind.coupon.promotion;

import com.northwind.coupon.billing.CardNetwork;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * A promotional coupon.
 *
 * <p>{@code discount} is the absolute amount taken off, in {@code settlementCurrency}.
 *
 * <p>{@code settlementCurrency} is new for COUPON-490. Sterling promotions keep {@code GBP};
 * the SEPA catalogue carries {@code EUR}. Existing coupons are constructed through the
 * back-compatible constructor below and default to {@code GBP}, so nothing in the sterling
 * catalogue changes.
 *
 * <p>{@code fundedBy} is the set of card networks whose interchange rebate pays for this
 * promotion. A redemption on any other network is spend with no funding behind it, which is
 * why {@link NetworkPromotionRules} refuses rather than defaulting.
 */
public record Coupon(
        String code,
        BigDecimal discount,
        String settlementCurrency,
        Set<CardNetwork> fundedBy
) {

    /** The settlement currency for the sterling catalogue, and the default. */
    public static final String GBP = "GBP";

    /** Euro settlement, for the SEPA catalogue. */
    public static final String EUR = "EUR";

    /** Minor units per major unit. Both GBP and EUR are two-decimal currencies. */
    private static final BigDecimal MINOR_UNITS_PER_MAJOR = new BigDecimal("100");

    /**
     * Back-compatible form, for the sterling catalogue and for call sites that predate
     * {@code settlementCurrency}.
     *
     * <p>Retained so this change stays <strong>additive</strong>: every existing construction
     * keeps compiling and keeps meaning exactly what it meant.
     */
    public Coupon(String code, BigDecimal discount, Set<CardNetwork> fundedBy) {
        this(code, discount, GBP, fundedBy);
    }

    /**
     * The discount as an integral number of minor units.
     *
     * <p>SEPA instructions carry amounts as integral minor units (ISO 20022
     * {@code InstdAmt} is expressed in the currency's smallest denomination), so a euro
     * promotion of &euro;24.90 settles as {@code 2490}. Sterling is expressed the same way for
     * consistency, because a single settlement pipeline handling two representations of the same
     * figure is how reconciliation breaks.
     *
     * <p>Truncates rather than rounds: a fraction of a cent cannot be instructed, and rounding
     * up would instruct more promotional spend than was agreed.
     */
    public BigDecimal discountMinorUnits() {
        return discount
                .multiply(MINOR_UNITS_PER_MAJOR)
                .setScale(0, RoundingMode.DOWN);
    }

    /** True for a promotion that settles through SEPA rather than Bacs/FPS. */
    public boolean isSepaSettled() {
        return EUR.equals(settlementCurrency);
    }
}
