package com.beaconstone.coupon.backfill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One shared instance, many threads: every call must get the line for its own input. */
class BackfillRunSummaryConcurrencyTest {

    private static final int THREADS = 16;
    private static final int CALLS = 20_000;

    private static final long[] STARTS = {
            1_704_067_200_000L, 1_718_064_000_000L, 1_727_222_400_000L,
            1_735_689_599_000L, 1_693_526_400_000L, 1_751_328_000_000L,
    };

    private final BackfillRunSummary summary = new BackfillRunSummary();

    @Test
    void everyConcurrentCallGetsTheLineForItsOwnInput() throws Exception {
        String[] expected = new String[STARTS.length];
        for (int i = 0; i < STARTS.length; i++) {
            expected[i] = lineFor(i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Boolean>> results = new ArrayList<>(CALLS);
            for (int call = 0; call < CALLS; call++) {
                int input = call % STARTS.length;
                results.add(pool.submit(() -> expected[input].equals(lineFor(input))));
            }

            int correct = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    correct++;
                }
            }
            assertEquals(CALLS, correct);
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    private String lineFor(int input) {
        Date started = new Date(STARTS[input]);
        Date finished = new Date(STARTS[input] + 3_600_000L);
        return summary.line("B-" + input, started, finished, input * 100);
    }
}
