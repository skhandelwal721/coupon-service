package com.northwind.coupon.report;

import com.northwind.coupon.redemption.RedemptionReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributionExportControllerTest {

    private final ReceiptStore receipts = new ReceiptStore();
    private final AttributionExportController controller = new AttributionExportController(
            new AttributionExport(new BigDecimal("25000.00")), receipts);

    @Test
    void servesAnEmptyExportBeforeAnyRedemptions() {
        AttributionExport.Export export = controller.forDate("2026-09-10");

        assertEquals("2026-09-10", export.date());
        assertTrue(export.rows().isEmpty());
        assertEquals(new BigDecimal("0.00"), export.total());
    }

    @Test
    void servesEveryRecordedReceiptAsARow() {
        receipts.record(receipt("rdm_1", "24.90"));
        receipts.record(receipt("rdm_2", "15.00"));

        AttributionExport.Export export = controller.forDate("2026-09-10");

        assertEquals(2, export.rows().size());
        assertEquals(new BigDecimal("39.90"), export.total());
        assertTrue(export.exceptions().isEmpty());
    }

    @Test
    void servesTheTotalOnItsOwnForTheDashboardTile() {
        receipts.record(receipt("rdm_1", "24.90"));
        receipts.record(receipt("rdm_2", "15.00"));

        AttributionExportController.Total total = controller.totalForDate("2026-09-10");

        assertEquals("2026-09-10", total.date());
        assertEquals(new BigDecimal("39.90"), total.total());
        assertEquals(2, total.rows());
    }

    @Test
    void recordsEachReceiptOnce() {
        receipts.record(receipt("rdm_1", "24.90"));
        receipts.record(receipt("rdm_1", "24.90"));

        assertEquals(2, receipts.size());
    }

    @Test
    void handsOutACopySoACallerCannotMutateTheStore() {
        receipts.record(receipt("rdm_1", "24.90"));

        assertTrue(receipts.all().size() == 1);
        assertEquals(1, receipts.size());
    }

    private static RedemptionReceipt receipt(String id, String discount) {
        return new RedemptionReceipt(id, "NW-VISA-10", "chg_1", "VISA",
                new BigDecimal(discount), "REDEEMED");
    }
}
