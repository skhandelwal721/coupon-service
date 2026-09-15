package com.northwind.coupon.fraud;

/**
 * Where velocity counters live.
 *
 * <p>An interface rather than a concrete map, because {@link InMemoryVelocityCounterStore} is
 * explicitly an interim implementation. ECS-2.2 requires rate-limiting and deduplication state
 * to live in the shared Redis cluster, not in process memory, and a Redis-backed implementation
 * drops in here without touching {@link VelocityGuard}. See COUPON-497.
 *
 * <p>Two properties any implementation must hold, and they are the reasons this type exists:
 *
 * <ol>
 *   <li><strong>Bounded.</strong> Counters expire and the store has a ceiling. An unbounded
 *       counter store is an infinite retention period for pseudonymised personal data
 *       (DPP-5.1) and a heap exhaustion risk on a Tier-1 request path (ECS-2.2).</li>
 *   <li><strong>Erasable.</strong> A single subject's counters can be removed on request, which
 *       is what makes GDPR Art. 17 satisfiable (DPP-5.2).</li>
 * </ol>
 */
public interface VelocityCounterStore {

    /**
     * Records one attempt against a key and returns the count within the current window.
     *
     * @return the number of attempts seen for this key, including this one
     */
    int increment(String key);

    /**
     * Removes every counter for a key.
     *
     * <p>The mechanism behind a data-subject erasure request. Keys are keyed hashes, so an
     * erasure is performed by recomputing the subject's fingerprint and forgetting it.
     */
    void forget(String key);

    /** How many keys are currently held. For the fraud dashboard and for capacity alarms. */
    int size();
}
