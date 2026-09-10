package com.northwind.coupon.report;

import com.northwind.coupon.redemption.RedemptionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the receipts the attribution export is built from.
 *
 * <p>Finance builds the daily promotional-spend export from redemption receipts. Until now the
 * only way to get them was the overnight warehouse load, which is why the export could not be
 * produced intraday. Recording each receipt as it completes lets
 * {@link AttributionExportController} serve the export on demand.
 *
 * <p>Held in memory. Redemption is already dependent on billing-service being up, so adding a
 * datastore to the checkout path would give us a second thing that can fail it — this keeps the
 * change off the critical path.
 */
@Component
public class ReceiptStore {

    private static final Logger log = LoggerFactory.getLogger(ReceiptStore.class);

    private final List<RedemptionReceipt> receipts = new CopyOnWriteArrayList<>();

    /** Records a completed redemption. Never throws — the redemption is already irreversible. */
    public void record(RedemptionReceipt receipt) {
        receipts.add(receipt);
        log.debug("recorded receipt for attribution redemptionId={} held={}",
                receipt.redemptionId(), receipts.size());
    }

    public List<RedemptionReceipt> all() {
        return List.copyOf(receipts);
    }

    public int size() {
        return receipts.size();
    }
}
