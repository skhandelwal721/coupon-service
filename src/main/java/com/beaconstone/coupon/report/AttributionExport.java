package com.beaconstone.coupon.report;

import com.beaconstone.coupon.redemption.RedemptionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the daily promotional-spend export for finance.
 *
 * <p>One row per redemption, and a total that finance books as the day's promotional spend.
 * The figure comes from {@code discount} on the redemption receipt, which
 * {@code docs/api/redemption.md} documents as an absolute currency amount.
 *
 * <p><strong>The plausibility check is a magnitude cap, not a semantic one.</strong> A discount
 * larger than {@link #MAX_PLAUSIBLE_DISCOUNT} is held as an exception on the assumption that a
 * four-figure discount is a data error. That catches a coupon configured wrongly. It does not
 * catch a discount figure that is <em>too small</em> — and a percentage arriving in a field
 * that should hold an amount is always too small.
 *
 * <p>Concretely: a 10% discount on a 249.00 order is 24.90 of real promotional spend. If the
 * receipt reported {@code 10.00} instead, this export would book 10.00, pass every check here,
 * and understate the day's promotional spend by roughly 60%. Finance would reconcile against
 * a number that is internally consistent and wrong.
 */
@Component
public class AttributionExport {

    /** Above this, a single discount is treated as a data error rather than a real promotion. */
    static final BigDecimal MAX_PLAUSIBLE_DISCOUNT = new BigDecimal("1000.00");

    private static final Logger log = LoggerFactory.getLogger(AttributionExport.class);

    public Export build(String date, List<RedemptionReceipt> receipts) {
        List<ExportRow> rows = new ArrayList<>();
        List<String> exceptions = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        long totalWholeUnits = 0;

        for (RedemptionReceipt receipt : receipts) {
            BigDecimal discount = receipt.discount();

            if (isImplausible(discount)) {
                exceptions.add(heldForReview(receipt, discount));
                continue;
            }

            rows.add(new ExportRow(receipt.redemptionId(), receipt.couponCode(), discount,
                    wholeUnits(discount)));
            total = total.add(discount);
            totalWholeUnits += wholeUnits(discount);
        }

        log.info("built attribution export date={} rows={} total={} totalWholeUnits={} "
                        + "exceptions={}",
                date, rows.size(), total, totalWholeUnits, exceptions.size());

        return new Export(date, rows, total, totalWholeUnits, exceptions);
    }

    /**
     * The plausibility ceiling, expressed once. A discount above it is a data error rather than
     * a real promotion — see the class javadoc for what this check does and does not catch.
     */
    private static boolean isImplausible(BigDecimal discount) {
        return discount.compareTo(MAX_PLAUSIBLE_DISCOUNT) > 0;
    }

    /** The exception line for a discount held out of the export. Wording unchanged. */
    private static String heldForReview(RedemptionReceipt receipt, BigDecimal discount) {
        return "redemption " + receipt.redemptionId() + " discount " + discount
                + " is above the plausible ceiling " + MAX_PLAUSIBLE_DISCOUNT;
    }

    /**
     * The discount as a whole number of currency units, for finance's close spreadsheet.
     *
     * <p>Finance asked for a whole-unit column: the close template sums a plain integer column
     * and does not want to format a decimal per row. The exact figure stays on the row in
     * {@code discount}, so this is an extra way of reading the same number.
     */
    private static long wholeUnits(BigDecimal discount) {
        return discount.longValue();
    }

    public record ExportRow(String redemptionId, String couponCode, BigDecimal discount,
                            long discountWholeUnits) {
    }

    public record Export(String date, List<ExportRow> rows, BigDecimal total,
                         long totalWholeUnits, List<String> exceptions) {
    }
}
