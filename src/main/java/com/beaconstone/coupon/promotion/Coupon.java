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
 * <p>{@code discount} is the absolute amount taken off, in {@code settlementCurrency}, for a
 * {@link DiscountType#FIXED} coupon. For a {@link DiscountType#PERCENTAGE} coupon it is unused
 * and the discount is derived from {@code percentageBps} against the charge subtotal — see
 * {@link #discountMinorUnitsFor(BigDecimal)}.
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
 * country codes a coupon may be redeemed from. An <strong>empty</strong> set means unrestricted.
 *
 * <p>{@code discountType} and {@code percentageBps} are new for COUPON-610, which introduces
 * percentage discounting to the catalogue for the first time. Every coupon that predates this
 * change is {@link DiscountType#FIXED} — an absolute amount, exactly as before — so the addition
 * is additive and the existing catalogue is unchanged. A {@link DiscountType#PERCENTAGE} coupon
 * carries its rate in {@code percentageBps} (basis points; 2000 = 20%) and its discount is a
 * function of the charge subtotal, not a constant.
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

    /** How a coupon's discount is computed. */
    public enum DiscountType {
        /** An absolute amount ({@code discount}) off, independent of the order. The original and default. */
        FIXED,
        /** A percentage ({@code percentageBps}) off the charge subtotal. New for COUPON-610. */
        PERCENTAGE
    }

    /** The settlement currency for the sterling catalogue, and the default. */
    public static final String GBP = "GBP";

    /** Euro settlement, for the SEPA catalogue. */
    public static final String EUR = "EUR";

    /** Minor units per major unit. Both GBP and EUR are two-decimal currencies. */
    private static final BigDecimal MINOR_UNITS_PER_MAJOR = new BigDecimal("100");

    /** Basis points in a whole (100%). 2000 bps = 20%. */
    private static final BigDecimal BPS_PER_WHOLE = new BigDecimal("10000");

    /**
     * The rounding applied when reducing a discount to whole minor units — COUPON-610.
     *
     * <p>{@link RoundingMode#DOWN} (truncate toward zero) is chosen deliberately and must stay
     * reconciled with billing-service's charge arithmetic:
     *
     * <ul>
     *   <li>billing-service publishes {@code subtotal + tax == total} as an invariant (asserted
     *       by {@code RedemptionAuditor}), and a percentage discount is applied to the
     *       <em>subtotal</em> billing-service returned — the same figure reconciliation uses.</li>
     *   <li>Truncating <em>down</em> guarantees the promotional deduction never exceeds the
     *       agreed percentage of that subtotal by even a sub-cent. Rounding up (or half-up) could
     *       instruct one minor unit more than the rate agrees, which over time diverges from what
     *       finance invoices the networks and shows up as an unreconciled promotional overspend.</li>
     *   <li>A fraction of a minor unit cannot be instructed on a SEPA/Bacs settlement anyway, so
     *       some truncation is unavoidable; doing it in the conservative direction is the safe,
     *       reconcilable default.</li>
     * </ul>
     *
     * <p>If billing-service ever documents a different rounding direction for discounts, this
     * constant is the single place to reconcile against it.
     */
    private static final RoundingMode DISCOUNT_ROUNDING = RoundingMode.DOWN;

    /**
     * Canonicalises {@code eligibleCountries} to upper-case and validates the discount shape.
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
        if (discountType == DiscountType.PERCENTAGE) {
            if (percentageBps <= 0 || percentageBps > 10000) {
                throw new IllegalArgumentException(
                        "percentageBps must be within (0, 10000] for a PERCENTAGE coupon: " + percentageBps);
            }
        }
    }

    /**
     * Back-compatible form, for the sterling catalogue and for call sites that predate
     * {@code settlementCurrency}. FIXED, GBP settlement, unrestricted.
     */
    public Coupon(String code, BigDecimal discount, Set<CardNetwork> fundedBy) {
        this(code, discount, GBP, fundedBy, Set.of(), DiscountType.FIXED, 0);
    }

    /**
     * Back-compatible form for the SEPA catalogue, which predates {@code eligibleCountries}.
     * FIXED, unrestricted.
     */
    public Coupon(String code, BigDecimal discount, String settlementCurrency,
                  Set<CardNetwork> fundedBy) {
        this(code, discount, settlementCurrency, fundedBy, Set.of(), DiscountType.FIXED, 0);
    }

    /**
     * Back-compatible form for country-restricted FIXED coupons (COUPON-573), which predate the
     * discount-type field. FIXED.
     */
    public Coupon(String code, BigDecimal discount, String settlementCurrency,
                  Set<CardNetwork> fundedBy, Set<String> eligibleCountries) {
        this(code, discount, settlementCurrency, fundedBy, eligibleCountries, DiscountType.FIXED, 0);
    }

    /**
     * Factory for a PERCENTAGE coupon — COUPON-610.
     *
     * @param percentageBps the rate in basis points (2000 = 20%).
     */
    public static Coupon percentage(String code, int percentageBps, String settlementCurrency,
                                    Set<CardNetwork> fundedBy, Set<String> eligibleCountries) {
        return new Coupon(code, BigDecimal.ZERO, settlementCurrency, fundedBy,
                eligibleCountries, DiscountType.PERCENTAGE, percentageBps);
    }

    /**
     * The FIXED discount as an integral number of minor units.
     *
    }

    /**
     * Factory for a PERCENTAGE coupon — COUPON-610.
     *
     * @param percentageBps the rate in basis points (2000 = 20%).
     */
    public static Coupon percentage(String code, int percentageBps, String settlementCurrency,
                                    Set<CardNetwork> fundedBy, Set<String> eligibleCountries) {
        return new Coupon(code, BigDecimal.ZERO, settlementCurrency, fundedBy,
                eligibleCountries, DiscountType.PERCENTAGE, percentageBps);
    }

    /**
     * The FIXED discount as an integral number of minor units.
     *
     * <p>Applies to FIXED coupons; for a PERCENTAGE coupon the amount depends on the order, so
     * callers must use {@link #discountMinorUnitsFor(BigDecimal)} instead. Kept for the entire
     * existing (FIXED) catalogue and existing call sites.
     *
     * <p>Truncates rather than rounds: a fraction of a cent cannot be instructed, and rounding
     * up would instruct more promotional spend than was agreed.
     */
    public BigDecimal discountMinorUnits() {
        if (discountType == DiscountType.PERCENTAGE) {
            throw new IllegalStateException(
                    "coupon " + code + " is PERCENTAGE; use discountMinorUnitsFor(subtotal)");
        }
        return discount
                .multiply(MINOR_UNITS_PER_MAJOR)
                .setScale(0, DISCOUNT_ROUNDING);
    }

    /**
     * The discount as an integral number of minor units, given the charge {@code subtotal} in
     * minor units.
     *
     * <p>For a FIXED coupon this is the fixed amount and {@code subtotal} is ignored, so callers
     * can use this one method for either type. For a PERCENTAGE coupon it is
     * {@code subtotal * percentageBps / 10000}, rounded with {@link #DISCOUNT_ROUNDING} —
     * truncated down so the deduction never exceeds the agreed percentage of the subtotal
     * billing-service returned, keeping it reconcilable against the charge. See
     * {@link #DISCOUNT_ROUNDING} for why the direction matters.
     */
    public BigDecimal discountMinorUnitsFor(BigDecimal subtotalMinorUnits) {
        if (discountType == DiscountType.FIXED) {
            return discountMinorUnits();
        }
        return subtotalMinorUnits
                .multiply(BigDecimal.valueOf(percentageBps))
                .divide(BPS_PER_WHOLE, 0, DISCOUNT_ROUNDING);
    }

    /**
     * The discount as an integral number of minor units, given the charge {@code subtotal} in
     * minor units.
     *
     * <p>For a FIXED coupon this is the fixed amount and {@code subtotal} is ignored, so callers
     * can use this one method for either type. For a PERCENTAGE coupon it is
     * {@code subtotal * percentageBps / 10000}, truncated down for the same reason as above —
     * we never instruct more promotional spend than the rate agrees.
     */
    public BigDecimal discountMinorUnitsFor(BigDecimal subtotalMinorUnits) {
        if (discountType == DiscountType.FIXED) {
            return discountMinorUnits();
        }
        return subtotalMinorUnits
                .multiply(BigDecimal.valueOf(percentageBps))
                .divide(BPS_PER_WHOLE, 0, RoundingMode.DOWN);
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
     * <p>An unrestricted coupon is available everywhere; a restricted coupon needs a country to
     * match, and a {@code null}/blank country against a restricted coupon is not eligible.
     * Matching is case-insensitive.
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
