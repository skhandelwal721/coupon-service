package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The country restriction added in COUPON-573. Kept separate from {@code CouponTest} so the
 * new behaviour reads on its own.
 */
class CouponCountryRestrictionTest {

    private static Coupon restrictedTo(String... countries) {
        return new Coupon("BS-NL-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), Set.of(countries));
    }

    private static Coupon unrestricted() {
        return new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), Set.of());
    }

    @Test
    void anUnrestrictedCouponIsAvailableEverywhereIncludingWithNoCountry() {
        Coupon coupon = unrestricted();

        assertFalse(coupon.isCountryRestricted());
        assertTrue(coupon.isAvailableIn("NL"));
        assertTrue(coupon.isAvailableIn("DE"));
        assertTrue(coupon.isAvailableIn(null));
        assertTrue(coupon.isAvailableIn(""));
    }

    @Test
    void aRestrictedCouponIsAvailableOnlyInItsCountries() {
        Coupon coupon = restrictedTo("NL");

        assertTrue(coupon.isCountryRestricted());
        assertTrue(coupon.isAvailableIn("NL"));
        assertFalse(coupon.isAvailableIn("DE"));
        assertFalse(coupon.isAvailableIn("BE"));
    }

    @Test
    void countryMatchingIsCaseInsensitive() {
        Coupon coupon = restrictedTo("NL");

        assertTrue(coupon.isAvailableIn("nl"));
        assertTrue(coupon.isAvailableIn("Nl"));
        assertTrue(coupon.isAvailableIn("nL"));
    }

    /** A restriction that silently passes when the country is absent is not a restriction. */
    @Test
    void aRestrictedCouponRefusesWhenNoCountryIsGiven() {
        Coupon coupon = restrictedTo("NL");

        assertFalse(coupon.isAvailableIn(null));
        assertFalse(coupon.isAvailableIn(""));
        assertFalse(coupon.isAvailableIn("   "));
    }

    /** A null eligibleCountries set is normalised to "unrestricted", not a crash. */
    @Test
    void aNullCountrySetMeansUnrestricted() {
        Coupon coupon = new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA), null);

        assertFalse(coupon.isCountryRestricted());
        assertTrue(coupon.isAvailableIn("anywhere is wrong shape but unrestricted still passes"));
    }

    /** The back-compatible constructors keep producing unrestricted coupons. */
    @Test
    void theBackCompatibleConstructorsProduceUnrestrictedCoupons() {
        Coupon sterling = new Coupon("NW-VISA-10", new BigDecimal("10.00"), Set.of(CardNetwork.VISA));
        Coupon euro = new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD));

        assertFalse(sterling.isCountryRestricted());
        assertFalse(euro.isCountryRestricted());
    }
}
