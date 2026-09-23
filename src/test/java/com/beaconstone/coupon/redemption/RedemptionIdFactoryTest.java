package com.beaconstone.coupon.redemption;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** COUPON-618 — the factory draws and counts, and holds nothing else. */
class RedemptionIdFactoryTest {

    @Test
    void everyIdentifierCarriesThePrefix() {
        assertTrue(new RedemptionIdFactory().next().startsWith(RedemptionIdFactory.PREFIX));
    }

    @Test
    void identifiersAreNotRepeated() {
        RedemptionIdFactory factory = new RedemptionIdFactory();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(factory.next()), "identifier repeated at draw " + i);
        }
        assertEquals(1000, factory.issuedCount());
    }

    @Test
    void theCountTracksWhatWasIssued() {
        RedemptionIdFactory factory = new RedemptionIdFactory();

        assertEquals(0, factory.issuedCount());
        factory.next();
        factory.next();
        assertEquals(2, factory.issuedCount());
    }

    /**
     * COUPON-617's set was mutated from request threads without synchronisation. This is the case
     * that exercised it: 16 threads drawing at once, every identifier distinct and the count exact.
     */
    @Test
    void concurrentDrawsAreDistinctAndCountedExactly() throws Exception {
        RedemptionIdFactory factory = new RedemptionIdFactory();
        int threads = 16;
        int perThread = 500;
        Set<String> seen = Collections.newSetFromMap(new ConcurrentHashMap<>());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        seen.add(factory.next());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "draws did not complete");
        pool.shutdownNow();

        assertEquals(threads * perThread, seen.size(), "every concurrent draw is distinct");
        assertEquals(threads * perThread, factory.issuedCount(), "the count is exact under concurrency");
    }

    /**
     * The property COUPON-617 lacked: memory does not grow with the number issued. The factory
     * holds one counter, so 100,000 draws leave it the same size as one.
     */
    @Test
    void theFactoryHoldsNoPerIdentifierState() {
        RedemptionIdFactory factory = new RedemptionIdFactory();

        for (int i = 0; i < 100_000; i++) {
            factory.next();
        }
        assertEquals(100_000, factory.issuedCount());

        // Stated as a property rather than measured: no field on the factory is a collection, so
        // there is nowhere for per-identifier state to accumulate.
        for (java.lang.reflect.Field field : RedemptionIdFactory.class.getDeclaredFields()) {
            assertFalse(java.util.Collection.class.isAssignableFrom(field.getType())
                            || java.util.Map.class.isAssignableFrom(field.getType()),
                    "unexpected collection field: " + field.getName());
        }
    }
}
