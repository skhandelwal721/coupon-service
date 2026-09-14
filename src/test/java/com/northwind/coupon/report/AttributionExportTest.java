package com.northwind.coupon.report;

import com.northwind.coupon.redemption.RedemptionReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionExportTest {

    private final AttributionExport export = new AttributionExport();

    @Test
    void sumsDiscountsAsAbsoluteAmounts() {
        AttributionExport.Export result = export.build("2026-09-01", List.of(
                receipt("rdm_1", "10.00"),
                receipt("rdm_2", "25.00"),
                receipt("rdm_3", "15.00")));

        assertEquals(3, result.rows().size());
        assertEquals(new BigDecimal("50.00"), result.total());
        assertTrue(result.exceptions().isEmpty());
    }

    @Test
    void holdsAnImplausiblyLargeDiscountAsAnException() {
        AttributionExport.Export result = export.build("2026-09-01", List.of(
                receipt("rdm_9", "5000.00")));

        assertEquals(0, result.rows().size());
        assertEquals(1, result.exceptions().size());
        assertTrue(result.exceptions().get(0).contains("plausible ceiling"));
    }

    /**
     * Documents the gap in the plausibility check, deliberately.
     *
     * <p>The cap catches a discount that is too large. There is no floor, because a small
     * discount is a perfectly normal promotion — so a figure that is too small for a different
     * reason passes silently.
     *
     * <p>A 10% discount on a 249.00 order is 24.90 of promotional spend. A receipt reporting
     * {@code 10.00} books 10.00, produces no exception, and understates the day by 14.90 on
     * that order alone. Finance reconciles against a number that is internally consistent.
     */
    @Test
    void aTooSmallDiscountProducesNoException() {
        AttributionExport.Export result = export.build("2026-09-01", List.of(
                receipt("rdm_1", "10.00")));

        assertEquals(1, result.rows().size());
        assertEquals(new BigDecimal("10.00"), result.total());
        assertTrue(result.exceptions().isEmpty(),
                "there is no floor on this check, so an understated discount is invisible here");
    }

    /**
     * The ceiling regression for COUPON-490.
     *
     * <p>The plausibility ceiling is 1000.00 and it is expressed in major units. When
     * COUPON-490 put minor units on {@code discount}, an ordinary 24.90 promotion arrived as
     * 2490, cleared the ceiling, and was held as an exception instead of exported — so the
     * day's promotional spend silently lost its largest promotions.
     */
    @Test
    void exportsAnOrdinaryPromotionRatherThanHoldingIt() {
        AttributionExport.Export result = export.build("2026-09-14", List.of(
                receipt("rdm_1", "24.90"),
                receipt("rdm_2", "249.00")));

        assertEquals(2, result.rows().size(),
                "an ordinary promotion in major units must not exceed the plausibility ceiling");
        assertEquals(new BigDecimal("273.90"), result.total());
        assertTrue(result.exceptions().isEmpty());
    }

    /** Stated plainly: the same figures on the wrong basis would be held. */
    @Test
    void theCeilingIsExpressedInMajorUnits() {
        AttributionExport.Export onMinorUnits = export.build("2026-09-14", List.of(
                receipt("rdm_1", "2490")));

        assertEquals(0, onMinorUnits.rows().size());
        assertEquals(1, onMinorUnits.exceptions().size(),
                "this is what COUPON-490 did to every ordinary promotion");
    }

    private static RedemptionReceipt receipt(String id, String discount) {
        return new RedemptionReceipt(id, "NW-VISA-10", "chg_1",
                "VISA", new BigDecimal(discount), "REDEEMED");
    }
}
