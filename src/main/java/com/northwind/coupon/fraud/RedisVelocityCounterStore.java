package com.northwind.coupon.fraud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Velocity counters in the shared Redis cluster. <strong>The production store.</strong>
 *
 * <p>ECS-2.2 requires rate-limiting state here rather than in process memory, for a reason that
 * is not only about heap: a per-instance counter is an <em>n</em>-times-the-limit bypass for an
 * attacker spread across instances, and with {@code minInstances: 2} / {@code maxInstances: 12}
 * that is a factor of twelve. A rolling deploy also resets in-process counters, which is a
 * scheduled window in which the check does not work.
 *
 * <p>Fixed-window counter, the standard Redis construction: {@code INCR} the key, and set the
 * TTL on the write that creates it. The TTL is the retention policy — Redis expires the key
 * itself, so there is no sweep to run and nothing to bound in this process (DPP-5.1).
 *
 * <p><strong>Fails closed.</strong> If the cluster cannot be reached, the redemption is refused.
 * A velocity check that silently passes because its store is down is worse than no check,
 * because nothing says so. The alternative — allow on error — is how fraud controls quietly
 * stop working.
 */
@Component
@ConditionalOnProperty(name = "fraud.velocity.store", havingValue = "redis", matchIfMissing = true)
public class RedisVelocityCounterStore implements VelocityCounterStore {

    private static final Logger log = LoggerFactory.getLogger(RedisVelocityCounterStore.class);

    /** Keyspace prefix, so these keys are identifiable and separately expirable. */
    static final String NAMESPACE = "coupon:velocity:";

    private final StringRedisTemplate redis;
    private final Duration window;

    public RedisVelocityCounterStore(
            StringRedisTemplate redis,
            @Value("${fraud.velocity.windowMinutes:1440}") long windowMinutes) {
        this.redis = redis;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    public int increment(String key) {
        String namespaced = NAMESPACE + key;

        try {
            Long count = redis.opsForValue().increment(namespaced);

            if (count == null) {
                throw new CounterStoreUnavailableException(
                        "the velocity counter store returned no count — the redemption is"
                                + " refused rather than left unchecked");
            }

            if (count == 1L) {
                // Only on the write that created the key, so the window is fixed from the first
                // attempt rather than sliding forward on every one.
                redis.expire(namespaced, window);
            }

            return count.intValue();
        } catch (CounterStoreUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("velocity counter store is unreachable — refusing the redemption", e);
            throw new CounterStoreUnavailableException(
                    "the velocity counter store is unreachable — the redemption is refused"
                            + " rather than left unchecked", e);
        }
    }

    @Override
    public int peek(String key) {
        try {
            String value = redis.opsForValue().get(NAMESPACE + key);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("a velocity counter held a value that is not a count — treating as zero");
            return 0;
        } catch (RuntimeException e) {
            log.error("velocity counter store is unreachable — refusing the redemption", e);
            throw new CounterStoreUnavailableException(
                    "the velocity counter store is unreachable — the redemption is refused"
                            + " rather than left unchecked", e);
        }
    }

    @Override
    public void forget(String key) {
        Boolean removed = redis.delete(NAMESPACE + key);

        if (Boolean.TRUE.equals(removed)) {
            log.info("erased velocity counters for one key on request");
        }
    }

    /**
     * Not tracked in-process.
     *
     * <p>Running a keyspace scan on a request path to answer this would be worse than not
     * answering it. The cluster reports its own keyspace and memory metrics, and
     * {@code VelocityCounterStoreUtilisation} watches those.
     */
    @Override
    public int size() {
        return -1;
    }
}
