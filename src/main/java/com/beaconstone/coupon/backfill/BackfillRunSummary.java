package com.beaconstone.coupon.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Writes the one-line summary the promotions backfill logs when a batch finishes.
 *
 * <p>The line goes to the service log only. It is not persisted, published or returned to a
 * caller.
 *
 * <p>Backfill workers finish batches concurrently, so the formatter must be safe to share.
 * {@link DateTimeFormatter} is immutable and thread-safe; {@code SimpleDateFormat} is not.
 */
@Component
public class BackfillRunSummary {

    private static final Logger log = LoggerFactory.getLogger(BackfillRunSummary.class);

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    /** Logs the summary for a finished batch. */
    public void record(String batchId, Date started, Date finished, int rows) {
        log.info(line(batchId, started, finished, rows));
    }

    String line(String batchId, Date started, Date finished, int rows) {
        return "backfill batch " + batchId
                + " started=" + TIMESTAMP.format(started.toInstant())
                + " finished=" + TIMESTAMP.format(finished.toInstant())
                + " rows=" + rows;
    }
}
