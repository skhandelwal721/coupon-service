package com.northwind.coupon.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the daily promotional-spend export.
 *
 * <p>Finance asked for the export intraday rather than waiting for the overnight warehouse
 * load, so they can close the promotional-spend line on the same day it happened. This builds
 * it from the receipts {@link ReceiptStore} has recorded since the last restart.
 *
 * <p>Read-only. Nothing here books, charges or reverses anything.
 */
@RestController
@RequestMapping("/v1/reports/attribution")
public class AttributionExportController {

    private static final Logger log = LoggerFactory.getLogger(AttributionExportController.class);

    private final AttributionExport export;
    private final ReceiptStore receipts;

    public AttributionExportController(AttributionExport export, ReceiptStore receipts) {
        this.export = export;
        this.receipts = receipts;
    }

    /** The full export for a date: every row, plus the total finance books. */
    @GetMapping("/{date}")
    public AttributionExport.Export forDate(@PathVariable String date) {
        log.info("serving attribution export date={} receipts={}", date, receipts.size());
        return export.build(date, receipts.all());
    }

    /** Just the total, for the finance dashboard tile. */
    @GetMapping("/{date}/total")
    public Total totalForDate(@PathVariable String date) {
        AttributionExport.Export built = export.build(date, receipts.all());
        return new Total(date, built.total(), built.rows().size());
    }

    public record Total(String date, java.math.BigDecimal total, int rows) {
    }
}
