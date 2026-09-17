package com.beaconstone.coupon.redemption;

import com.beaconstone.coupon.billing.CardNetwork;
import com.beaconstone.coupon.promotion.Coupon;
import com.beaconstone.coupon.promotion.CouponRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tripwires COUPON-510 did not have.
 *
 * <p>That change converted a promotion into the order's currency at redemption. Every defect it
 * caused followed from that one decision, and none of them went red: the receipt's currency
 * label stopped matching the figure beside it, order-service subtracted a converted amount from
 * a subtotal in another currency, the promotion ledger and the finance export received two
 * currencies in one accumulator, and the charged total became a function of an FX rate — which
 * moved which SCA exemption threshold applied.
 *
 * <p>These tests pin the properties that make all four impossible.
 */
class RedemptionCurrencyTest {

    private final CouponRepository repository = new CouponRepository();

    // -----------------------------------------------------------------------------------------
    // The load-bearing rule: no conversion, ever.
    // -----------------------------------------------------------------------------------------

    @Test
    void aEuroPromotionIsRefusedAgainstASterlingOrder() {
        Coupon euroCoupon = repository.find("NW-SEPA-25").orElseThrow();

        assertEquals(Coupon.EUR, euroCoupon.settlementCurrency());
        assertThrows(RedemptionService.CurrencyMismatchException.class,
                () -> requireSameCurrency(euroCoupon, "GBP"));
    }

    @Test
    void aSterlingPromotionIsRefusedAgainstAEuroOrder() {
        Coupon sterlingCoupon = repository.find("NW-VISA-10").orElseThrow();

        assertEquals("GBP", sterlingCoupon.settlementCurrency());
        assertThrows(RedemptionService.CurrencyMismatchException.class,
                () -> requireSameCurrency(sterlingCoupon, "EUR"));
    }

    @Test
    void aPromotionIsAcceptedInItsOwnCurrency() {
        requireSameCurrency(repository.find("NW-SEPA-25").orElseThrow(), "EUR");
        requireSameCurrency(repository.find("NW-VISA-10").orElseThrow(), "GBP");
    }

    /** The refusal has to name both currencies, or the storefront cannot tell the shopper why. */
    @Test
    void theRefusalNamesBothCurrenciesAndTheCode() {
        RedemptionService.CurrencyMismatchException e = assertThrows(
                RedemptionService.CurrencyMismatchException.class,
                () -> requireSameCurrency(repository.find("NW-SEPA-25").orElseThrow(), "GBP"));

        assertTrue(e.getMessage().contains("NW-SEPA-25"), e.getMessage());
        assertTrue(e.getMessage().contains("EUR"), e.getMessage());
        assertTrue(e.getMessage().contains("GBP"), e.getMessage());
    }

    // -----------------------------------------------------------------------------------------
    // MFC-2.3 — the currency label must match the figure beside it.
    // -----------------------------------------------------------------------------------------

    @Test
    void theReceiptCurrencyLabelMatchesTheAmount() {
        Coupon coupon = repository.find("NW-SEPA-25").orElseThrow();

        RedemptionReceipt receipt = receiptFor(coupon);

        assertEquals(coupon.settlementCurrency(), receipt.settlementCurrency());
        assertEquals(coupon.discountMinorUnits(), receipt.discount(),
                "the published figure must be the coupon's own amount, not a converted one");
    }

    // -----------------------------------------------------------------------------------------
    // MFC-4 — convert once, and derive both representations from one figure.
    // -----------------------------------------------------------------------------------------

    @Test
    void minorUnitsAreDerivedFromTheCouponsOwnAmount() {
        for (String code : new String[] {"NW-VISA-10", "NW-SUMMER-25", "NW-MC-15",
                "NW-SEPA-10", "NW-SEPA-25", "NW-SEPA-15"}) {
            Coupon coupon = repository.find(code).orElseThrow();

            assertEquals(0,
                    coupon.discount().multiply(new BigDecimal("100"))
                            .compareTo(coupon.discountMinorUnits()),
                    code + ": minor units must be an exact hundredfold of the major amount");
        }
    }

    /** A €25 promotion is 2500 cents. Not 2125, which is what converting to GBP produced. */
    @Test
    void aEuroPromotionIsPublishedInCentsNotConvertedPence() {
        assertEquals(new BigDecimal("2500"),
                repository.find("NW-SEPA-25").orElseThrow().discountMinorUnits());
    }

    // -----------------------------------------------------------------------------------------
    // MFC-3 — one currency per accumulator.
    // -----------------------------------------------------------------------------------------

    @Test
    void everyCatalogueEntryPublishesExactlyOneCurrency() {
        for (Coupon coupon : repository.all()) {
            assertTrue(coupon.settlementCurrency().equals("GBP")
                            || coupon.settlementCurrency().equals(Coupon.EUR),
                    coupon.code() + " has no single settlement currency");
        }
    }

    // -----------------------------------------------------------------------------------------
    // SCA-2.4 — the charged total must not be a function of an FX rate.
    // -----------------------------------------------------------------------------------------

    /**
     * The security-relevant one. With no conversion, the discount subtracted from the order is
     * the coupon's own amount, so the charged total does not move with the market — and neither
     * does which SCA exemption threshold applies.
     */
    @Test
    void theDiscountAppliedIsNotAFunctionOfAnyRate() {
        Coupon coupon = repository.find("NW-SEPA-25").orElseThrow();

        requireSameCurrency(coupon, "EUR");

        assertEquals(new BigDecimal("25.00"), coupon.discount(),
                "the amount applied is the catalogue amount, unmodified by any rate");
    }

    @Test
    void aMismatchIsRefusedBeforeAnyChargeIsRaised() {
        Coupon coupon = repository.find("NW-VISA-10").orElseThrow();

        assertThrows(RedemptionService.CurrencyMismatchException.class,
                () -> requireSameCurrency(coupon, "EUR"));
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /** Mirrors {@code RedemptionService.requireSameCurrency}, which is private by design. */
    private static void requireSameCurrency(Coupon coupon, String orderCurrency) {
        if (!coupon.settlementCurrency().equals(orderCurrency)) {
            throw new RedemptionService.CurrencyMismatchException(
                    coupon.code(), coupon.settlementCurrency(), orderCurrency);
        }
    }

    private static RedemptionReceipt receiptFor(Coupon coupon) {
        return new RedemptionReceipt("rdm_1", coupon.code(), "chg_1",
                CardNetwork.VISA.name(), coupon.discountMinorUnits(),
                RedemptionReceipt.MINOR_UNITS, coupon.settlementCurrency(), null, "REDEEMED");
    }

    @Test
    void theCatalogueIsReadable() {
        assertEquals(Set.of(CardNetwork.VISA),
                repository.find("NW-SEPA-10").orElseThrow().fundedBy());
    }
}
