package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.EnumSet;
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

    // The sterling catalogue, always present and unaffected by any launch flag.
    private static final Map<String, Coupon> STERLING_COUPONS = Map.of(
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
                    Set.of(CardNetwork.MASTERCARD)));

    static final String NL_COUPON_CODE = "BS-NL-20";

    /**
     * The EU percentage coupon, COUPON-610 — the first percentage discount in the catalogue.
     * 20% off (2000 bps), EUR settlement, offered EU-wide (not country-restricted). Registered
     * only when {@code promotions.euPercentage.enabled} is true, which is on in Production only.
     */
    static final String EU_PERCENTAGE_CODE = "BS-EUP-20";
    private static final int EU_PERCENTAGE_BPS = 2000; // 20%

    private final Map<String, Coupon> coupons;

    /**
     * @param nlLaunchEnabled     {@code promotions.nlLaunch.enabled}. Controls whether the
     *                            Netherlands-only BS-NL-20 coupon is in the catalogue at all.
     * @param amexEuropeEnabled   {@code payments.amexEurope.enabled}. Controls whether AMEX is a
     *                            funded network on the EUR storefront offers.
     * @param euPercentageEnabled {@code promotions.euPercentage.enabled}. Controls whether the
     *                            EU-wide 20% coupon BS-EUP-20 is in the catalogue. On in
     *                            Production only; when off the code cannot be resolved or redeemed.
     */
    public CouponRepository(
            @Value("${promotions.nlLaunch.enabled:false}") boolean nlLaunchEnabled,
            @Value("${payments.amexEurope.enabled:false}") boolean amexEuropeEnabled,
            @Value("${promotions.euPercentage.enabled:false}") boolean euPercentageEnabled) {

        // The networks a EUR storefront accepts. Visa and Mastercard always; AMEX only when the
        // AMEX-in-Europe option is enabled. An offer advertised storefront-wide has to be funded
        // on exactly the networks the storefront accepts (COUPON-551).
        Set<CardNetwork> eurAcceptedNetworks = EnumSet.of(CardNetwork.VISA, CardNetwork.MASTERCARD);
        if (amexEuropeEnabled) {
            eurAcceptedNetworks.add(CardNetwork.AMEX);
        }
        Set<CardNetwork> eurFunding = Set.copyOf(eurAcceptedNetworks);

        Map<String, Coupon> catalogue = new HashMap<>(STERLING_COUPONS);

        // EU acquisition campaign, COUPON-550/551. Fixed €20 off, funded on the EUR accepted set.
        catalogue.put("BS-EU-20", new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                eurFunding));

        // Netherlands-only acquisition coupon, COUPON-573. Present only when its launch flag is on.
        if (nlLaunchEnabled) {
            catalogue.put(NL_COUPON_CODE, new Coupon(NL_COUPON_CODE, new BigDecimal("20.00"),
                    Coupon.EUR, eurFunding, Set.of("NL")));
        }

        // EU percentage coupon, COUPON-610. 20% off, EU-wide, funded on the EUR accepted set.
        // Percentage discounting is new to the catalogue; see Coupon.DiscountType.
        if (euPercentageEnabled) {
            catalogue.put(EU_PERCENTAGE_CODE, Coupon.percentage(
                    EU_PERCENTAGE_CODE, EU_PERCENTAGE_BPS, Coupon.EUR, eurFunding, Set.of()));
        }

        this.coupons = Map.copyOf(catalogue);
    }

    /**
     * Back-compatible form: NL launch + AMEX flags, EU percentage off. Retained so callers that
     * predate COUPON-610 keep compiling and keep meaning what they meant.
     */
    public CouponRepository(boolean nlLaunchEnabled, boolean amexEuropeEnabled) {
        this(nlLaunchEnabled, amexEuropeEnabled, false);
    }

    /**
     * Back-compatible form: control only the NL launch; AMEX and EU percentage off.
     */
    public CouponRepository(boolean nlLaunchEnabled) {
        this(nlLaunchEnabled, false, false);
    }

    /** Test/backfill form: everything off. */
    public CouponRepository() {
        this(false, false, false);
    }

    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(coupons.get(code));
    }

    public Iterable<Coupon> all() {
        return coupons.values();
    }
}
