package com.northwind.coupon.chargeback;

import com.northwind.coupon.ledger.PromotionLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reverses promotion liability for chargebacks the acquirers sent us overnight.
 *
 * <p>A chargeback means the charge a redemption was reconciled against has been clawed back, so
 * the discount we booked against the funding network is no longer owed. This job walks the
 * night's chargeback file and reverses each one out of {@link PromotionLedger}.
 *
 * <p><strong>Unattributable references are skipped, not fatal.</strong> Before COUPON-481 a
 * single reference from an unmapped acquirer threw and aborted the batch, so the thousands of
 * chargebacks we could attribute went unreversed too. The batch now completes and reports what
 * it skipped.
 */
@Component
public class ChargebackReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ChargebackReconciliationJob.class);

    private final ChargebackMatcher matcher;
    private final PromotionLedger ledger;

    public ChargebackReconciliationJob(ChargebackMatcher matcher, PromotionLedger ledger) {
        this.matcher = matcher;
        this.ledger = ledger;
    }

    public Result reconcile(List<Chargeback> chargebacks) {
        List<String> skipped = new ArrayList<>();
        int reversed = 0;

        for (Chargeback chargeback : chargebacks) {
            ChargebackMatcher.Acquirer acquirer = matcher.acquirerOf(chargeback.acquirerReference());

            if (acquirer == ChargebackMatcher.Acquirer.UNKNOWN) {
                skipped.add(chargeback.acquirerReference());
                continue;
            }

            ledger.reverse(chargeback.fundingNetwork(), chargeback.discount());
            reversed++;
        }

        log.info("reconciled chargebacks reversed={} skipped={}", reversed, skipped.size());

        if (!skipped.isEmpty()) {
            log.warn("chargebacks we could not attribute references={}", skipped);
        }

        return new Result(reversed, skipped);
    }

    /** One line of the acquirer's nightly chargeback file. */
    public record Chargeback(
            String acquirerReference,
            String redemptionId,
            String fundingNetwork,
            BigDecimal discount
    ) {
    }

    public record Result(int reversed, List<String> skipped) {
    }
}
