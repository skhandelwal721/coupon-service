package com.northwind.coupon.promotion;

import com.northwind.coupon.billing.CardNetwork;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class CouponRepository {

    private static final Map<String, Coupon> COUPONS = Map.of(
            "NW-VISA-10", new Coupon("NW-VISA-10", new BigDecimal("10.00"),
                    Set.of(CardNetwork.VISA)),
            "NW-SUMMER-25", new Coupon("NW-SUMMER-25", new BigDecimal("25.00"),
                    Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD)),
            "NW-MC-15", new Coupon("NW-MC-15", new BigDecimal("15.00"),
                    Set.of(CardNetwork.MASTERCARD)));

    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(COUPONS.get(code));
    }

    public Iterable<Coupon> all() {
        return COUPONS.values();
    }
}
