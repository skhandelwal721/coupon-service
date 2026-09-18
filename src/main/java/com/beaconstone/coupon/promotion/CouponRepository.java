package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The promotion catalogue. Fixed for the fixture — the real repository reads the
 * catalogue from the promotions service.
 */
@Repository
public class CouponRepository {

    // The catalogue that is always present, independent of any launch flag.
    private static final Map<String, Coupon> BASE_COUPONS = Map.of(
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

    /**
     * The Netherlands acquisition coupon, COUPON-573. Beacon Stone prefix, EUR settlement,
     * funded on every network the storefront accepts (same reasoning as BS-EU-20, COUPON-551).
     *
     * <p>Unlike BS-EU-20 it is <strong>country-restricted to NL</strong> — it is a
     * Netherlands-only offer, so a shopper on any other storefront is refused rather than
     * discounted. The restriction is enforced in {@code RedemptionService} from the request's
     * {@code billingCountry}.
     *
     * <p>Registered only when {@code promotions.nlLaunch.enabled} is true. That flag is enabled
     * in Production and left off everywhere else, so this launches in Production only — a
     * catalogue entry that is not present cannot be resolved, redeemed, or charged against.
     */
    static final String NL_COUPON_CODE = "BS-NL-20";

    private static final Coupon NL_COUPON = new Coupon(
            NL_COUPON_CODE, new BigDecimal("20.00"), Coupon.EUR,
            Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD),
            Set.of("NL"));

    private final Map<String, Coupon> coupons;

    /**
     * @param nlLaunchEnabled {@code promotions.nlLaunch.enabled}. Defaults to {@code false} so
     *                        the coupon is absent unless a profile turns it on — Production does.
     */
    public CouponRepository(
            @Value("${promotions.nlLaunch.enabled:false}") boolean nlLaunchEnabled) {
        Map<String, Coupon> catalogue = new HashMap<>(BASE_COUPONS);
        if (nlLaunchEnabled) {
            catalogue.put(NL_COUPON_CODE, NL_COUPON);
        }
        this.coupons = Map.copyOf(catalogue);
    }

    /** Test/backfill form: base catalogue only, NL launch off. */
    public CouponRepository() {
        this(false);
    }

    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(coupons.get(code));
    }

    public Iterable<Coupon> all() {
        return coupons.values();
    }
}
