package com.northwind.coupon.promotion;

import com.northwind.coupon.billing.CardNetwork;

import java.math.BigDecimal;
import java.util.Set;

/**
 * A promotional coupon.
 *
 * <p>{@code fundedBy} is the set of card networks whose interchange rebate pays for this
 * promotion. A redemption on any other network is spend with no funding behind it, which is
 * why {@link NetworkPromotionRules} refuses rather than defaulting.
 */
public record Coupon(
        String code,
        BigDecimal discount,
        Set<CardNetwork> fundedBy
) {
}
