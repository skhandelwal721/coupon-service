package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
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

    private final Map<String, Coupon> coupons;

    /**
     * @param nlLaunchEnabled  {@code promotions.nlLaunch.enabled}. Controls whether the
     *                         Netherlands-only BS-NL-20 coupon is in the catalogue at all.
     * @param amexEuropeEnabled {@code payments.amexEurope.enabled}. Controls whether AMEX is a
     *                         funded network on the EUR storefront offers. When AMEX is not
     *                         offered in Europe, the EUR offers must NOT list AMEX as funded —
     *                         otherwise an AMEX charge would be treated as funded when AMEX is not
     *                         actually being offered. The funding set is therefore built to match
     *                         the flag, not hard-coded.
     */
    public CouponRepository(
            @Value("${promotions.nlLaunch.enabled:false}") boolean nlLaunchEnabled,
            @Value("${payments.amexEurope.enabled:false}") boolean amexEuropeEnabled) {

        // The networks a EUR storefront accepts. Visa and Mastercard always; AMEX only when the
        // AMEX-in-Europe option is enabled. An offer advertised storefront-wide has to be funded
        // on exactly the networks the storefront accepts (COUPON-551): no fewer (or a shopper is
        // charged then refused), and no more (or a network is treated as funded when it is not
        // being offered — PAY-8100 flag-gating fix).
        Set<CardNetwork> eurAcceptedNetworks = EnumSet.of(CardNetwork.VISA, CardNetwork.MASTERCARD);
        if (amexEuropeEnabled) {
            eurAcceptedNetworks.add(CardNetwork.AMEX);
        }
        Set<CardNetwork> eurFunding = Set.copyOf(eurAcceptedNetworks);

        Map<String, Coupon> catalogue = new HashMap<>(STERLING_COUPONS);

        // EU acquisition campaign, COUPON-550/551. Funded on exactly the networks the EUR
        // storefronts accept, which now depends on the AMEX-in-Europe flag.
        catalogue.put("BS-EU-20", new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                eurFunding));

        // Netherlands-only acquisition coupon, COUPON-573. Present only when its own launch flag
        // is on; funded on the same EUR accepted-network set (so AMEX funding here is likewise
        // gated on the AMEX-in-Europe flag).
        if (nlLaunchEnabled) {
            catalogue.put(NL_COUPON_CODE, new Coupon(NL_COUPON_CODE, new BigDecimal("20.00"),
                    Coupon.EUR, eurFunding, Set.of("NL")));
        }

        // COUPON-622. Every entry is checked against Coupon#isWellFormed before the catalogue is
        // published. An entry that fails is malformed, not an entry worth nothing, and a published
        // entry is resolvable — so it would be acted on. Failing here means the fault surfaces when
        // the catalogue is assembled at startup, which a deploy shows, rather than as a zero-value
        // redemption nobody is alerted to.
        this.coupons = Map.copyOf(publishable(catalogue));
    }

    /**
     * Back-compatible form: control only the NL launch, AMEX-in-Europe off. Retained so existing
     * callers (and tests) that predate the AMEX flag keep compiling and keep meaning what they
     * meant — the EUR offers funded on Visa+Mastercard only.
     */
    public CouponRepository(boolean nlLaunchEnabled) {
        this(nlLaunchEnabled, false);
    }

    /** Test/backfill form: NL launch off, AMEX-in-Europe off. */
    public CouponRepository() {
        this(false, false);
    }

    /**
     * The publication gate — COUPON-622.
     *
     * <p>Returns the catalogue unchanged when every entry is well formed, and refuses otherwise,
     * naming the codes that failed so the fault is actionable without a debugger. The message
     * carries catalogue codes, which are published identifiers, and nothing else.
     */
    static Map<String, Coupon> publishable(Map<String, Coupon> catalogue) {
        List<String> malformed = catalogue.values().stream()
                .filter(coupon -> !coupon.isWellFormed())
                .map(Coupon::code)
                .sorted()
                .toList();

        if (!malformed.isEmpty()) {
            throw new IllegalStateException(
                    "catalogue entries do not record an amount and cannot be published: " + malformed);
        }
        return catalogue;
    }

    public Optional<Coupon> find(String code) {
        return Optional.ofNullable(coupons.get(code));
    }

    public Iterable<Coupon> all() {
        return coupons.values();
    }
}
