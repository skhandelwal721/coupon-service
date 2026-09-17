package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The promotion catalogue. Fixed for the fixture — the real repository reads the
 * catalogue from the promotions service.
 */
@Repository
public class CouponRepository {

    private static final Map<String, Coupon> COUPONS = Map.of(
            // Sterling catalogue. Unchanged — these use the back-compatible constructor and
            // default to GBP settlement.
            "NW-VISA-10", new Coupon("NW-VISA-10", new BigDecimal("10.00"),
                    Set.of(CardNetwork.VISA)),
            "NW-SUMMER-25", new Coupon("NW-SUMMER-25", new BigDecimal("25.00"),
                    Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD)),
            "NW-MC-15", new Coupon("NW-MC-15", new BigDecimal("15.00"),
                    Set.of(CardNetwork.MASTERCARD)),

            // SEPA catalogue, COUPON-490. DE/FR/NL/ES/IE storefronts.
            "NW-SEPA-10", new Coupon("NW-SEPA-10", new BigDecimal("10.00"), Coupon.EUR,
                    Set.of(CardNetwork.VISA)),
            "NW-SEPA-25", new Coupon("NW-SEPA-25", new BigDecimal("25.00"), Coupon.EUR,
                    Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD)),
            "NW-SEPA-15", new Coupon("NW-SEPA-15", new BigDecimal("15.00"), Coupon.EUR,
                    Set.of(CardNetwork.MASTERCARD)),

            // EU acquisition campaign, COUPON-550. Requested by growth for the DE/FR/NL
            // storefronts. Same shape as the SEPA entries above, and the first code to carry
            // the Beacon Stone prefix — see RedemptionRequest#couponCode for why NW- stays.
            //
            // Funded on Visa AND Mastercard, per the campaign's funding agreements. COUPON-550
            // listed Visa alone, which did not match the agreements and made the offer
            // unredeemable for a Mastercard shopper — see COUPON-551. A storefront-wide offer
            // has to be funded on every network that storefront accepts.
            "BS-EU-20", new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                    Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD)));

    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(COUPONS.get(code));
    }

    public Iterable<Coupon> all() {
        return COUPONS.values();
    }
}
