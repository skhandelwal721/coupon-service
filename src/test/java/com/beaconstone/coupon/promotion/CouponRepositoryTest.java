package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouponRepositoryTest {

    /** The pattern {@code RedemptionRequest#couponCode} enforces at the edge. */
    private static final String REQUEST_PATTERN = "^(BS|NW)-[A-Z]{2,4}-\\d{2}$";

    // Base catalogue, NL launch off — the no-arg constructor keeps the pre-COUPON-573 behaviour.
    private final CouponRepository repository = new CouponRepository();

    @Test
    void resolvesTheNewEuAcquisitionCoupon() {
        Coupon coupon = repository.find("BS-EU-20").orElseThrow();

        assertEquals("BS-EU-20", coupon.code());
        assertEquals(new BigDecimal("20.00"), coupon.discount());
    }

    @Test
    void theEuAcquisitionCouponSettlesInEuro() {
        Coupon coupon = repository.find("BS-EU-20").orElseThrow();

        assertEquals(Coupon.EUR, coupon.settlementCurrency());
        assertTrue(coupon.isSepaSettled());
    }

    /**
     * Regression for COUPON-551.
     *
     * <p>COUPON-550 funded this offer on Visa alone, and this test asserted that Mastercard
     * was excluded — so the catalogue and the suite agreed with each other and both disagreed
     * with the campaign's funding agreements. The offer is advertised to every shopper on the
     * DE/FR/NL storefronts, so it has to be funded on every network those storefronts accept.
     *
     * <p>Enumerates {@link CardNetwork#values()} rather than listing the two networks we
     * support today, so adding a third network to the platform fails here until this
     * campaign's funding for it is confirmed.
     */
    @Test
    void theEuAcquisitionCouponIsFundedOnEveryNetworkTheStorefrontAccepts() {
        Coupon coupon = repository.find("BS-EU-20").orElseThrow();

        for (CardNetwork network : CardNetwork.values()) {
            assertTrue(coupon.fundedBy().contains(network),
                    "BS-EU-20 is advertised storefront-wide but is not funded on " + network
                            + " — a shopper paying with it is charged and then refused");
        }
    }

    @Test
    void convertsToMinorUnitsLikeEveryOtherCatalogueEntry() {
        assertEquals(new BigDecimal("2000"),
                repository.find("BS-EU-20").orElseThrow().discountMinorUnits());
    }

    /** The existing catalogue is untouched. */
    @Test
    void theExistingCatalogueIsUnchanged() {
        assertEquals(new BigDecimal("10.00"),
                repository.find("NW-VISA-10").orElseThrow().discount());
        assertEquals(new BigDecimal("25.00"),
                repository.find("NW-SUMMER-25").orElseThrow().discount());
        assertEquals(new BigDecimal("15.00"),
                repository.find("NW-MC-15").orElseThrow().discount());
        assertEquals(new BigDecimal("10.00"),
                repository.find("NW-SEPA-10").orElseThrow().discount());
        assertEquals(new BigDecimal("25.00"),
                repository.find("NW-SEPA-25").orElseThrow().discount());
        assertEquals(new BigDecimal("15.00"),
                repository.find("NW-SEPA-15").orElseThrow().discount());
    }

    /**
     * The existing catalogue is unrestricted — no entry gained a country restriction. This is
     * the guarantee that COUPON-573 is additive: only the new BS-NL-20 is country-scoped.
     */
    @Test
    void theExistingCatalogueIsNotCountryRestricted() {
        for (String code : new String[] {
                "NW-VISA-10", "NW-MC-15", "NW-SEPA-10", "NW-SEPA-25", "NW-SEPA-15", "BS-EU-20"}) {
            assertFalse(repository.find(code).orElseThrow().isCountryRestricted(),
                    code + " must stay catalogue-wide — COUPON-573 only restricts BS-NL-20");
        }
    }

    @Test
    void anUnknownCodeResolvesToNothing() {
        assertTrue(repository.find("BS-NOPE-99").isEmpty());
    }

    /**
     * The new code has to match {@code RedemptionRequest}'s pattern, or it can never be
     * redeemed — a catalogue entry the edge validator rejects is a promotion that silently
     * does not exist.
     */
    @Test
    void theNewCodeMatchesTheRequestPattern() {
        assertTrue(repository.find("BS-EU-20").orElseThrow().code().matches(REQUEST_PATTERN),
                "BS-EU-20 must be submittable through POST /v1/redemptions");
    }

    /**
     * Widening the pattern to accept {@code BS-} must not stop accepting {@code NW-}. Those
     * codes are printed on cards and running in live campaigns; a customer holding one has to
     * keep being able to redeem it.
     *
     * <p>Scoped to the SEPA and network entries. {@code NW-SUMMER-25} does <em>not</em> match
     * the pattern — {@code SUMMER} is six letters against the {@code {2,4}} the validator
     * allows — and it did not match before this change either. That is a pre-existing gap in
     * the catalogue, left exactly as it was: this change adds a coupon, and quietly repairing
     * an unrelated one would hide it.
     */
    @Test
    void theLegacyPrefixIsStillAccepted() {
        assertTrue("NW-VISA-10".matches(REQUEST_PATTERN));
        assertTrue("NW-MC-15".matches(REQUEST_PATTERN));
        assertTrue("NW-SEPA-10".matches(REQUEST_PATTERN));
        assertTrue("NW-SEPA-25".matches(REQUEST_PATTERN));
        assertTrue("NW-SEPA-15".matches(REQUEST_PATTERN));
    }

    // -----------------------------------------------------------------------------------------
    // COUPON-573 — Netherlands launch, BS-NL-20, gated on promotions.nlLaunch.enabled.
    // -----------------------------------------------------------------------------------------

    /**
     * With the launch flag off — the default, and every environment except Production — the
     * coupon is absent. A code that is not in the catalogue cannot be resolved, so it cannot be
     * redeemed or charged against. This is what "production only" means in code.
     */
    @Test
    void theNlCouponIsAbsentWhenTheLaunchFlagIsOff() {
        CouponRepository off = new CouponRepository(false);

        assertTrue(off.find("BS-NL-20").isEmpty(),
                "BS-NL-20 must not exist unless promotions.nlLaunch.enabled is true");
    }

    /** With the launch flag on — Production — the coupon resolves. */
    @Test
    void theNlCouponResolvesWhenTheLaunchFlagIsOn() {
        CouponRepository on = new CouponRepository(true);
        Coupon coupon = on.find("BS-NL-20").orElseThrow();

        assertEquals("BS-NL-20", coupon.code());
        assertEquals(new BigDecimal("20.00"), coupon.discount());
        assertEquals(new BigDecimal("2000"), coupon.discountMinorUnits());
    }

    @Test
    void theNlCouponSettlesInEuro() {
        Coupon coupon = new CouponRepository(true).find("BS-NL-20").orElseThrow();

        assertEquals(Coupon.EUR, coupon.settlementCurrency());
        assertTrue(coupon.isSepaSettled());
    }

    /** The NL coupon is restricted to the Netherlands, and to nowhere else. */
    @Test
    void theNlCouponIsRestrictedToTheNetherlands() {
        Coupon coupon = new CouponRepository(true).find("BS-NL-20").orElseThrow();

        assertTrue(coupon.isCountryRestricted());
        assertTrue(coupon.isAvailableIn("NL"));
        assertTrue(coupon.isAvailableIn("nl"), "country matching must be case-insensitive");
        assertFalse(coupon.isAvailableIn("DE"));
        assertFalse(coupon.isAvailableIn("FR"));
        assertFalse(coupon.isAvailableIn(null),
                "a restricted coupon with no country must not silently pass");
    }

    /**
     * Same reasoning as BS-EU-20 (COUPON-551): a coupon advertised on the storefront has to be
     * funded on every network that storefront accepts, or a shopper paying on an unfunded
     * network is charged and then refused. Enumerates {@link CardNetwork#values()} so a new
     * network fails here until BS-NL-20's funding for it is confirmed.
     */
    @Test
    void theNlCouponIsFundedOnEveryNetworkTheStorefrontAccepts() {
        Coupon coupon = new CouponRepository(true).find("BS-NL-20").orElseThrow();

        for (CardNetwork network : CardNetwork.values()) {
            assertTrue(coupon.fundedBy().contains(network),
                    "BS-NL-20 is advertised on the NL storefront but is not funded on " + network);
        }
    }

    /** The NL code must be submittable through the edge validator. */
    @Test
    void theNlCodeMatchesTheRequestPattern() {
        assertTrue("BS-NL-20".matches(REQUEST_PATTERN),
                "BS-NL-20 must be submittable through POST /v1/redemptions");
    }

    /** Turning the launch on must not disturb the rest of the catalogue. */
    @Test
    void enablingTheNlLaunchLeavesTheExistingCatalogueUntouched() {
        CouponRepository on = new CouponRepository(true);

        assertEquals(new BigDecimal("20.00"), on.find("BS-EU-20").orElseThrow().discount());
        assertEquals(new BigDecimal("10.00"), on.find("NW-VISA-10").orElseThrow().discount());
        assertFalse(on.find("BS-EU-20").orElseThrow().isCountryRestricted(),
                "BS-EU-20 stays catalogue-wide even with the NL launch on");
    }
}
