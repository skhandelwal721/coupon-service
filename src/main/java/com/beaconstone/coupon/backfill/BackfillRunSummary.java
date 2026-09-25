package com.beaconstone.coupon.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Writes the one-line summary the promotions backfill logs when a batch finishes.
 *
 * <p>The line goes to the service log only. It is not persisted, published or returned to a
 * caller.
 */
@Component
public class BackfillRunSummary {

    private static final Logger log = LoggerFactory.getLogger(BackfillRunSummary.class);

    /** One formatter for the component, rather than one per batch. */
    private final SimpleDateFormat timestamp = utc("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** Logs the summary for a finished batch. */
    public void record(String batchId, Date started, Date finished, int rows) {
        log.info(line(batchId, started, finished, rows));
    }

    String line(String batchId, Date started, Date finished, int rows) {
        return "backfill batch " + batchId
                + " started=" + timestamp.format(started)
                + " finished=" + timestamp.format(finished)
                + " rows=" + rows;
    }

    private static SimpleDateFormat utc(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }
}
