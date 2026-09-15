package com.northwind.coupon.fraud;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound and the expiry are the whole point of this class, so they are what is asserted.
 *
 * <p>COUPON-491 held these counters in two plain {@code ConcurrentHashMap}s with no eviction —
 * an {@code OutOfMemoryError} on a Tier-1 path (ECS-2.2) and an infinite retention period for
 * pseudonymised personal data (DPP-5.1).
 */
class InMemoryVelocityCounterStoreTest {

    /** A clock the test moves by hand, so expiry is asserted rather than waited for. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-09-14T10:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();

    private InMemoryVelocityCounterStore store(Duration window, int maxKeys) {
        return new InMemoryVelocityCounterStore(window, maxKeys, clock);
    }

    @Test
    void countsAttemptsPerKey() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 1000);

        assertEquals(1, store.increment("a"));
        assertEquals(2, store.increment("a"));
        assertEquals(1, store.increment("b"));
        assertEquals(2, store.size());
    }

    // ---------------------------------------------------------------------------------------
    // Retention — DPP-5.1
    // ---------------------------------------------------------------------------------------

    @Test
    void aCounterExpiresAfterItsWindow() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 1000);

        assertEquals(1, store.increment("a"));
        assertEquals(2, store.increment("a"));

        clock.advance(Duration.ofHours(25));

        assertEquals(1, store.increment("a"), "the window reopened, so the count restarts");
    }

    @Test
    void aCounterSurvivesInsideItsWindow() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 1000);

        store.increment("a");
        clock.advance(Duration.ofHours(23));

        assertEquals(2, store.increment("a"));
    }

    @Test
    void sweepingRemovesExpiredKeysSoAnIdleProcessDoesNotHoldThem() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 1000);

        store.increment("a");
        store.increment("b");
        assertEquals(2, store.size());

        clock.advance(Duration.ofHours(25));

        assertEquals(2, store.sweepExpired());
        assertEquals(0, store.size(), "no eviction policy means infinite retention (DPP-5.1)");
    }

    // ---------------------------------------------------------------------------------------
    // Erasability — DPP-5.2, GDPR Art. 17
    // ---------------------------------------------------------------------------------------

    @Test
    void aSingleSubjectsCountersCanBeErasedOnRequest() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 1000);

        store.increment("subject");
        store.increment("subject");
        store.increment("someone-else");

        store.forget("subject");

        assertEquals(1, store.size());
        assertEquals(1, store.increment("subject"), "erased, so the count starts again");
    }

    @Test
    void forgettingAnUnknownKeyIsHarmless() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 1000);

        store.forget("never-seen");

        assertEquals(0, store.size());
    }

    // ---------------------------------------------------------------------------------------
    // The bound — ECS-2.2
    // ---------------------------------------------------------------------------------------

    @Test
    void sweepsExpiredKeysToStayUnderTheCeilingRatherThanGrowing() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 3);

        store.increment("a");
        store.increment("b");
        store.increment("c");

        // Everything so far is now expired, so a new key sweeps rather than exceeding.
        clock.advance(Duration.ofHours(25));

        assertEquals(1, store.increment("d"));
        assertEquals(1, store.size());
    }

    /**
     * Fails closed. At the ceiling with nothing to evict we refuse, rather than dropping a live
     * counter — dropping one silently switches the fraud check off for whoever it belonged to.
     */
    @Test
    void refusesRatherThanEvictingALiveCounterAtTheCeiling() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 2);

        store.increment("a");
        store.increment("b");

        InMemoryVelocityCounterStore.CounterStoreExhaustedException e = assertThrows(
                InMemoryVelocityCounterStore.CounterStoreExhaustedException.class,
                () -> store.increment("c"));

        assertTrue(e.getMessage().contains("refused rather than"),
                "the message has to say which way it fails: " + e.getMessage());
        assertEquals(2, store.size(),
                "the refused key is rolled back, so it neither overstates the store nor"
                        + " leaves a count a retry did not earn");
    }

    @Test
    void existingCountersStillIncrementWhenTheStoreIsFull() {
        InMemoryVelocityCounterStore store = store(Duration.ofHours(24), 2);

        store.increment("a");
        store.increment("b");

        assertEquals(2, store.increment("a"), "an existing key adds no cardinality");
    }
}
