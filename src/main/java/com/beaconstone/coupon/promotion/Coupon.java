package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
 *
 * <p>{@code eligibleCountries} is new for COUPON-573. It is the set of ISO 3166-1 alpha-2
 * country codes a coupon may be redeemed from. An <strong>empty</strong> set means
 * unrestricted — the catalogue-wide behaviour every coupon had before this change — so the
 * addition is additive and the existing catalogue is untouched. A non-empty set restricts the
 * coupon to those countries; see {@link #isAvailableIn(String)} and the country gate in
 * {@code RedemptionService}. Country matching is case-insensitive and codes are held upper-case.
 *
 * <p>{@code discountType} and {@code percentageBps} are new for COUPON-610. A {@code FIXED}
 * coupon takes an absolute amount off, which is what every coupon in the catalogue does today and
 * is the value the existing constructors produce. A {@code PERCENTAGE} coupon expresses its
 * discount as a rate in basis points instead, and its amount is computed from a subtotal supplied
 * by the caller rather than held on the record — see {@link #discountMinorUnitsFor(BigDecimal)}.
 */
public record Coupon(
        String code,
        BigDecimal discount,
        String settlementCurrency,
        Set<CardNetwork> fundedBy,
        Set<String> eligibleCountries,
        DiscountType discountType,
        int percentageBps
) {

    /** How a coupon's discount is expressed. */
    public enum DiscountType {
        /** An absolute amount ({@code discount}), independent of the subtotal. The default. */
        FIXED,
        /** A rate ({@code percentageBps}) applied to a subtotal. New for COUPON-610. */
        PERCENTAGE
    }

    /** The settlement currency for the sterling catalogue, and the default. */
    public static final String GBP = "GBP";

    /** Euro settlement, for the SEPA catalogue. */
    public static final String EUR = "EUR";

    /** Minor units per major unit. Both GBP and EUR are two-decimal currencies. */
    private static final BigDecimal MINOR_UNITS_PER_MAJOR = new BigDecimal("100");

    /** Basis points in a whole. 2000 bps = 20%. */
    private static final BigDecimal BPS_PER_WHOLE = new BigDecimal("10000");

    /**
     * Canonicalises {@code eligibleCountries} to upper-case so the restriction is matched
     * case-insensitively regardless of what the storefront submits or the catalogue declares.
     * A {@code null} set is treated as unrestricted.
     */
    public Coupon {
        eligibleCountries = eligibleCountries == null
                ? Set.of()
                : eligibleCountries.stream()
                        .map(c -> c.toUpperCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());

        if (discountType == null) {
            discountType = DiscountType.FIXED;
        }
        if (discountType == DiscountType.PERCENTAGE
                && (percentageBps <= 0 || percentageBps > 10000)) {
            throw new IllegalArgumentException(
                    "percentageBps must be within (0, 10000] for a PERCENTAGE coupon: " + percentageBps);
        }
    }

    /**
     * Back-compatible form, for the sterling catalogue and for call sites that predate
     * {@code settlementCurrency}.
     *
     * <p>Retained so this change stays <strong>additive</strong>: every existing construction
     * keeps compiling and keeps meaning exactly what it meant — GBP settlement, unrestricted.
     */
    public Coupon(String code, BigDecimal discount, Set<CardNetwork> fundedBy) {
        this(code, discount, GBP, fundedBy, Set.of(), DiscountType.FIXED, 0);
    }

    /**
     * Back-compatible form for the SEPA catalogue, which predates {@code eligibleCountries}.
     *
     * <p>Retained for the same reason as the sterling form above: the euro entries added in
     * COUPON-490/550 keep constructing and keep being catalogue-wide (unrestricted).
     */
    public Coupon(String code, BigDecimal discount, String settlementCurrency,
                  Set<CardNetwork> fundedBy) {
        this(code, discount, settlementCurrency, fundedBy, Set.of(), DiscountType.FIXED, 0);
    }

    /**
     * Back-compatible form for the country-restricted entries added in COUPON-573, which predate
     * {@code discountType}. FIXED, so those entries keep the amount and behaviour they have today.
     */
    public Coupon(String code, BigDecimal discount, String settlementCurrency,
                  Set<CardNetwork> fundedBy, Set<String> eligibleCountries) {
        this(code, discount, settlementCurrency, fundedBy, eligibleCountries, DiscountType.FIXED, 0);
    }

    /**
     * Builds a PERCENTAGE coupon at {@code percentageBps} basis points (2000 = 20%).
     *
     * <p>{@code discount} is zero for this form, because the amount is not a property of the
     * coupon: it is computed per call by {@link #discountMinorUnitsFor(BigDecimal)}.
     */
    public static Coupon percentage(String code, int percentageBps, String settlementCurrency,
                                    Set<CardNetwork> fundedBy, Set<String> eligibleCountries) {
        return new Coupon(code, BigDecimal.ZERO, settlementCurrency, fundedBy,
                eligibleCountries, DiscountType.PERCENTAGE, percentageBps);
    }

    /**
     * The amount for this coupon as an integral number of minor units, given a subtotal in minor
     * units.
     *
     * <p>A {@code FIXED} coupon ignores {@code subtotalMinorUnits} and returns exactly what
     * {@link #discountMinorUnits()} returns, so a caller can use this one method for either type
     * and the existing catalogue behaves identically through it.
     *
     * <p>A {@code PERCENTAGE} coupon returns {@code subtotal * percentageBps / 10000}. The
     * division truncates with {@link RoundingMode#DOWN}, matching the truncation
     * {@link #discountMinorUnits()} has always applied, so both types round the same way and a
     * fraction of a minor unit is never carried.
     */
    public BigDecimal discountMinorUnitsFor(BigDecimal subtotalMinorUnits) {
        if (discountType == DiscountType.FIXED) {
            return discountMinorUnits();
        }
        return subtotalMinorUnits
                .multiply(BigDecimal.valueOf(percentageBps))
                .divide(BPS_PER_WHOLE, 0, RoundingMode.DOWN);
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
        if (discountType == DiscountType.PERCENTAGE) {
            // A PERCENTAGE coupon holds discount == ZERO, so without this the method would
            // quietly answer 0 for a coupon that has a perfectly good amount, just not one that
            // can be known without a subtotal. Fail loudly instead of returning a wrong figure.
            // The message carries no operand — not the code, not the rate, not the caller's
            // arguments. A thrown message is the one string on this path that reaches a log
            // without passing through a formatter, so it states the contract and nothing else.
            throw new IllegalStateException(
                    "PERCENTAGE coupon has no fixed amount; use discountMinorUnitsFor(subtotal)");
        }
        return discount
                .multiply(MINOR_UNITS_PER_MAJOR)
                .setScale(0, RoundingMode.DOWN);
    }

    /** True for a promotion that settles through SEPA rather than Bacs/FPS. */
    public boolean isSepaSettled() {
        return EUR.equals(settlementCurrency);
    }

    /** True when this coupon is restricted to a specific set of countries. */
    public boolean isCountryRestricted() {
        return !eligibleCountries.isEmpty();
    }

    /**
     * Whether this coupon may be redeemed from {@code country} (ISO 3166-1 alpha-2).
     *
     * <p>An unrestricted coupon ({@link #isCountryRestricted()} false) is available everywhere,
     * so a missing {@code country} is fine for it. A restricted coupon needs a country to match:
     * a {@code null} or blank {@code country} against a restricted coupon is <em>not</em>
     * eligible, because a restriction that silently passes when the input is absent is not a
     * restriction. Matching is case-insensitive.
     */
    public boolean isAvailableIn(String country) {
        if (!isCountryRestricted()) {
            return true;
        }
        if (country == null || country.isBlank()) {
            return false;
        }
        return eligibleCountries.contains(country.toUpperCase(Locale.ROOT));
    }
}
