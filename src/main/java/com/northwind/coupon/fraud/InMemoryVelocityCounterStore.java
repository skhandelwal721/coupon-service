package com.northwind.coupon.fraud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-memory velocity counters, bounded by both time and size.
 *
 * <h2>What COUPON-491 got wrong</h2>
 *
 * <p>It used two plain {@code ConcurrentHashMap} instances with no eviction. At 19,760 discounted
 * orders a day and roughly 593,000 redemptions a month (BCT-2), with a key per
 * device-origin-card-coupon combination, those maps grow for the lifetime of the process. Two
 * separate problems, not one:
 *
 * <ul>
 *   <li><strong>ECS-2.2 / heap.</strong> Unbounded per-request state on a Tier-1 request path is
 *       an {@code OutOfMemoryError} with a date on it.</li>
 *   <li><strong>DPP-5.1 / retention.</strong> A store with no eviction policy has an infinite
 *       retention period, and these keys are derived from personal data. Infinite retention is
 *       non-compliant by construction, regardless of heap.</li>
 * </ul>
 *
 * <h2>What this does instead</h2>
 *
 * <p>Every counter carries the instant it was first seen and expires after
 * {@code fraud.velocity.window}. Expired entries are evicted lazily on access and swept
 * whenever the store is near its ceiling, so an idle process does not hold yesterday's keys.
 *
 * <p>The ceiling — {@code fraud.velocity.maxTrackedKeys} — is a hard bound. When a sweep cannot
 * get below it, the store <strong>refuses to grow</strong> and reports the key as over-limit
 * rather than evicting a live counter. That direction is deliberate: dropping a live counter
 * silently disables the fraud check for that attacker, whereas refusing is visible, alarmed, and
 * fails closed.
 *
 * <h2>Local development only</h2>
 *
 * <p><strong>This is not the production store.</strong> ECS-2.2 requires velocity state in the
 * shared Redis cluster, and {@link RedisVelocityCounterStore} is the default — this one is
 * selected only by setting {@code fraud.velocity.store: memory} explicitly, which the
 * production configuration does not do.
 *
 * <p>The reason is not only heap. A per-instance counter is an <em>n</em>-times-the-limit bypass
 * for an attacker spread across instances, and a rolling deploy resets every counter. Those are
 * correctness gaps in the fraud check, not capacity concerns, and no amount of bounding fixes
 * them.
 */
@Component
@ConditionalOnProperty(name = "fraud.velocity.store", havingValue = "memory")
public class InMemoryVelocityCounterStore implements VelocityCounterStore {

    private static final Logger log =
            LoggerFactory.getLogger(InMemoryVelocityCounterStore.class);

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private final Duration window;
    private final int maxTrackedKeys;
    private final Clock clock;

    public InMemoryVelocityCounterStore(
            @Value("${fraud.velocity.windowMinutes:1440}") long windowMinutes,
            @Value("${fraud.velocity.maxTrackedKeys:250000}") int maxTrackedKeys) {
        this(Duration.ofMinutes(windowMinutes), maxTrackedKeys, Clock.systemUTC());
    }

    InMemoryVelocityCounterStore(Duration window, int maxTrackedKeys, Clock clock) {
        this.window = window;
        this.maxTrackedKeys = maxTrackedKeys;
        this.clock = clock;
    }

    @Override
    public int peek(String key) {
        Counter counter = counters.get(key);
        return counter == null || counter.hasExpired(Instant.now(clock), window)
                ? 0
                : counter.count();
    }

    @Override
    public int increment(String key) {
        Instant now = Instant.now(clock);

        Counter counter = counters.compute(key, (k, existing) ->
                existing == null || existing.hasExpired(now, window)
                        ? new Counter(now)
                        : existing.increment());

        if (counters.size() > maxTrackedKeys) {
            sweepExpired(now);
        }

        if (counters.size() > maxTrackedKeys) {
            // Fail closed. Refusing is visible; evicting a live counter would silently switch
            // the fraud check off for whoever it belonged to.
            //
            // Roll back the key we just added first: we are refusing this attempt, so leaving
            // its counter behind would both overstate the store and mean a retry sees a count
            // it never earned.
            if (counter.count() == 1) {
                counters.remove(key);
            }

            log.error("velocity counter store is at its ceiling keys={} max={} — refusing",
                    counters.size(), maxTrackedKeys);
            throw new CounterStoreUnavailableException(
                    "velocity counter store holds " + counters.size() + " keys, ceiling is "
                            + maxTrackedKeys + " — redemptions are refused rather than"
                            + " left unchecked");
        }

        return counter.count();
    }

    @Override
    public void forget(String key) {
        if (counters.remove(key) != null) {
            log.info("erased velocity counters for one key on request");
        }
    }

    @Override
    public int size() {
        return counters.size();
    }

    /** Evicts expired entries. Also exposed so a scheduled sweep can call it directly. */
    public int sweepExpired() {
        return sweepExpired(Instant.now(clock));
    }

    private int sweepExpired(Instant now) {
        int before = counters.size();

        for (Iterator<Map.Entry<String, Counter>> it = counters.entrySet().iterator();
                it.hasNext(); ) {
            if (it.next().getValue().hasExpired(now, window)) {
                it.remove();
            }
        }

        int evicted = before - counters.size();
        if (evicted > 0) {
            log.debug("swept expired velocity counters evicted={} remaining={}",
                    evicted, counters.size());
        }
        return evicted;
    }

    /** One key's attempt count and the instant its window opened. */
    private record Counter(int count, Instant firstSeen) {

        Counter(Instant firstSeen) {
            this(1, firstSeen);
        }

        Counter increment() {
            return new Counter(count + 1, firstSeen);
        }

        boolean hasExpired(Instant now, Duration window) {
            return firstSeen.plus(window).isBefore(now);
        }
    }
}
