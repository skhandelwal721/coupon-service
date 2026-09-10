package com.northwind.coupon.report;

import com.northwind.coupon.redemption.RedemptionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * larger than {@code report.maxPlausibleDiscount} is held as an exception on the assumption that a
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

    private static final Logger log = LoggerFactory.getLogger(AttributionExport.class);

    /**
     * Above this, a single discount is treated as a data error rather than a real promotion.
     *
     * <p>Configurable via {@code report.maxPlausibleDiscount}. The hard-coded 1000.00 was
     * holding legitimate rows once the enterprise catalogue went live — a 40% coupon on a
     * five-figure basket is a real promotion, and every one of them landed in the exceptions
     * list for someone to clear by hand.
     */
    private final BigDecimal maxPlausibleDiscount;

    public AttributionExport(
            @Value("${report.maxPlausibleDiscount:25000.00}") BigDecimal maxPlausibleDiscount) {
        this.maxPlausibleDiscount = maxPlausibleDiscount;
    }

    public Export build(String date, List<RedemptionReceipt> receipts) {
        List<ExportRow> rows = new ArrayList<>();
        List<String> exceptions = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (RedemptionReceipt receipt : receipts) {
            BigDecimal discount = receipt.discount();

            if (discount.compareTo(maxPlausibleDiscount) > 0) {
                exceptions.add("redemption " + receipt.redemptionId() + " discount " + discount
                        + " is above the plausible ceiling " + maxPlausibleDiscount);
                continue;
            }

            rows.add(new ExportRow(receipt.redemptionId(), receipt.couponCode(), discount));
            total = total.add(discount);
        }

        log.info("built attribution export date={} rows={} total={} exceptions={}",
                date, rows.size(), total, exceptions.size());

        return new Export(date, rows, total, exceptions);
    }

    public record ExportRow(String redemptionId, String couponCode, BigDecimal discount) {
    }

    public record Export(String date, List<ExportRow> rows, BigDecimal total, List<String> exceptions) {
    }
}
