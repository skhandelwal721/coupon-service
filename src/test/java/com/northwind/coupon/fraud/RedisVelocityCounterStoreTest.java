package com.northwind.coupon.fraud;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The production store (ECS-2.2).
 *
 * <p>What is asserted here is the construction — namespacing, the TTL being set on the write
 * that creates the key, and failing closed — because those are the properties the fraud control
 * depends on. The counting itself is Redis's.
 */
class RedisVelocityCounterStoreTest {

    private static final String KEY = "digest|digest|digest|NW-VISA-10";
    private static final String NAMESPACED = RedisVelocityCounterStore.NAMESPACE + KEY;

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisVelocityCounterStore store;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new RedisVelocityCounterStore(redis, 1440);
    }

    @Test
    void incrementsANamespacedKey() {
        when(values.increment(NAMESPACED)).thenReturn(4L);

        assertEquals(4, store.increment(KEY));
        verify(values).increment(NAMESPACED);
    }

    /**
     * The TTL is the retention policy (DPP-5.1), and it is set on the write that creates the
     * key so the window is fixed from the first attempt rather than sliding forward on every
     * one. A sliding window would let a steady attacker never expire.
     */
    @Test
    void setsTheTtlOnTheWriteThatCreatesTheKey() {
        when(values.increment(NAMESPACED)).thenReturn(1L);

        store.increment(KEY);

        verify(redis).expire(NAMESPACED, Duration.ofMinutes(1440));
    }

    @Test
    void doesNotResetTheTtlOnSubsequentAttempts() {
        when(values.increment(NAMESPACED)).thenReturn(2L);

        store.increment(KEY);

        verify(redis, never()).expire(any(String.class), any(Duration.class));
    }

    // ---------------------------------------------------------------------------------------
    // Fails closed.
    // ---------------------------------------------------------------------------------------

    /**
     * The security-critical property. A velocity check that silently passes because its store
     * is down is worse than no check, because nothing says so.
     */
    @Test
    void refusesTheRedemptionWhenTheClusterIsUnreachable() {
        when(values.increment(NAMESPACED))
                .thenThrow(new RedisConnectionFailureException("no route to cluster"));

        VelocityCounterStore.CounterStoreUnavailableException e = assertThrows(
                VelocityCounterStore.CounterStoreUnavailableException.class,
                () -> store.increment(KEY));

        assertTrue(e.getMessage().contains("refused rather than left unchecked"),
                "the message has to say which way it fails: " + e.getMessage());
    }

    @Test
    void refusesTheRedemptionWhenTheClusterReturnsNoCount() {
        when(values.increment(NAMESPACED)).thenReturn(null);

        assertThrows(VelocityCounterStore.CounterStoreUnavailableException.class,
                () -> store.increment(KEY));
    }

    @Test
    void peekFailsClosedToo() {
        when(values.get(NAMESPACED))
                .thenThrow(new RedisConnectionFailureException("no route to cluster"));

        assertThrows(VelocityCounterStore.CounterStoreUnavailableException.class,
                () -> store.peek(KEY));
    }

    // ---------------------------------------------------------------------------------------
    // peek — used across a key rotation (DPP-11.6).
    // ---------------------------------------------------------------------------------------

    @Test
    void peekReadsTheCountWithoutRecordingAnAttempt() {
        when(values.get(NAMESPACED)).thenReturn("7");

        assertEquals(7, store.peek(KEY));
        verify(values, never()).increment(any(String.class));
    }

    @Test
    void peekTreatsAnUnknownKeyAsZero() {
        when(values.get(NAMESPACED)).thenReturn(null);

        assertEquals(0, store.peek(KEY));
    }

    @Test
    void peekTreatsAnUnparseableValueAsZeroRatherThanFailing() {
        when(values.get(NAMESPACED)).thenReturn("not-a-number");

        assertEquals(0, store.peek(KEY));
    }

    // ---------------------------------------------------------------------------------------
    // Erasure — DPP-5.2, GDPR Art. 17.
    // ---------------------------------------------------------------------------------------

    @Test
    void forgetDeletesTheNamespacedKey() {
        when(redis.delete(NAMESPACED)).thenReturn(true);

        store.forget(KEY);

        verify(redis).delete(eq(NAMESPACED));
    }

    /** Documented: the cluster reports its own keyspace metrics, which is what the alarm uses. */
    @Test
    void doesNotScanTheKeyspaceToReportSize() {
        assertEquals(-1, store.size());
    }
}
