package com.northwind.coupon.promotion;

import com.northwind.coupon.billing.CardNetwork;

import java.math.BigDecimal;
import java.util.Set;

/**
 * A promotional coupon.
 *
 * <p>{@code discount} is the percentage taken off. The coupon codes have always been named for
 * it — {@code NW-VISA-10} is ten percent — so the figures below are unchanged; only the basis
 * is now stated rather than assumed.
 *
 * <p>{@code fundedBy} is the set of card networks whose interchange rebate pays for this
 * promotion.
 */
public record Coupon(
        String code,
        BigDecimal discount,
        Set<CardNetwork> fundedBy
) {

    /** The absolute amount this coupon takes off a given subtotal. */
    public BigDecimal amountOff(BigDecimal subtotal) {
        return subtotal
                .multiply(discount)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }
}
