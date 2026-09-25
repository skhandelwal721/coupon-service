package com.beaconstone.coupon.backfill;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackfillRunSummaryTest {

    private final BackfillRunSummary summary = new BackfillRunSummary();

    @Test
    void formatsBothTimestampsInUtc() {
        Date started = new Date(1_727_222_400_000L);
        Date finished = new Date(1_727_226_000_000L);

        assertEquals(
                "backfill batch B-0412 started=2024-09-25T00:00:00Z finished=2024-09-25T01:00:00Z rows=1840",
                summary.line("B-0412", started, finished, 1840));
    }

    @Test
    void formatsAZeroRowBatch() {
        Date at = new Date(1_704_067_200_000L);

        assertEquals(
                "backfill batch B-0001 started=2024-01-01T00:00:00Z finished=2024-01-01T00:00:00Z rows=0",
                summary.line("B-0001", at, at, 0));
    }
}
