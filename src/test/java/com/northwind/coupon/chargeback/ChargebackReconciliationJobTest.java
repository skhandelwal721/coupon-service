package com.northwind.coupon.chargeback;

import com.northwind.coupon.billing.CardNetwork;
import com.northwind.coupon.ledger.PromotionLedger;
import com.northwind.coupon.redemption.RedemptionReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargebackReconciliationJobTest {

    private final PromotionLedger ledger = new PromotionLedger();
    private final ChargebackReconciliationJob job =
            new ChargebackReconciliationJob(new ChargebackMatcher(), ledger);

    @Test
    void reversesAnAttributableChargebackOutOfTheLedger() {
        ledger.book(receipt("VISA", "24.90"));

        ChargebackReconciliationJob.Result result = job.reconcile(List.of(
                chargeback("wp_4f8a21c7", "VISA", "24.90")));

        assertEquals(1, result.reversed());
        assertTrue(result.skipped().isEmpty());
        assertEquals(new BigDecimal("0.00"), ledger.liabilityFor(CardNetwork.VISA));
    }

    @Test
    void reversesAcrossBothAcquirers() {
        ledger.book(receipt("VISA", "24.90"));
        ledger.book(receipt("MASTERCARD", "15.00"));

        ChargebackReconciliationJob.Result result = job.reconcile(List.of(
                chargeback("wp_4f8a21c7", "VISA", "24.90"),
                chargeback("ad_9b31f7ca", "MASTERCARD", "15.00")));

        assertEquals(2, result.reversed());
        assertEquals(new BigDecimal("0.00"), ledger.liabilityFor(CardNetwork.VISA));
        assertEquals(new BigDecimal("0.00"), ledger.liabilityFor(CardNetwork.MASTERCARD));
    }

    /**
     * The reason for the change. One unmapped acquirer used to abort the batch, leaving every
     * chargeback after it unreversed as well.
     */
    @Test
    void completesTheBatchWhenOneReferenceCannotBeAttributed() {
        ledger.book(receipt("VISA", "24.90"));
        ledger.book(receipt("MASTERCARD", "15.00"));

        ChargebackReconciliationJob.Result result = job.reconcile(List.of(
                chargeback("amex_7c2b91de", "VISA", "24.90"),
                chargeback("wp_4f8a21c7", "VISA", "24.90"),
                chargeback("ad_9b31f7ca", "MASTERCARD", "15.00")));

        assertEquals(2, result.reversed());
        assertEquals(List.of("amex_7c2b91de"), result.skipped());
        assertEquals(new BigDecimal("0.00"), ledger.liabilityFor(CardNetwork.VISA));
        assertEquals(new BigDecimal("0.00"), ledger.liabilityFor(CardNetwork.MASTERCARD));
    }

    @Test
    void reportsAnEmptyFileAsNothingToDo() {
        ChargebackReconciliationJob.Result result = job.reconcile(List.of());

        assertEquals(0, result.reversed());
        assertTrue(result.skipped().isEmpty());
    }

    private static ChargebackReconciliationJob.Chargeback chargeback(
            String reference, String network, String discount) {
        return new ChargebackReconciliationJob.Chargeback(
                reference, "rdm_1", network, new BigDecimal(discount));
    }

    private static RedemptionReceipt receipt(String network, String discount) {
        return new RedemptionReceipt("rdm_1", "NW-VISA-10", "chg_1", network,
                new BigDecimal(discount), "REDEEMED");
    }
}
