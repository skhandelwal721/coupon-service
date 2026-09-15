package com.northwind.coupon.fraud;

/**
 * Where velocity counters live.
 *
 * <p>ECS-2.2 requires rate-limiting and deduplication state in the shared Redis cluster rather
 * than in process memory, and {@link RedisVelocityCounterStore} is the implementation that
 * satisfies it. {@link InMemoryVelocityCounterStore} exists for local development only and has
 * to be selected explicitly.
 *
 * <p>Three properties any implementation must hold:
 *
 * <ol>
 *   <li><strong>Bounded.</strong> Counters expire. An unbounded counter store is an infinite
 *       retention period for pseudonymised personal data (DPP-5.1) and, in process memory, a
 *       heap exhaustion risk on a Tier-1 request path (ECS-2.2).</li>
 *   <li><strong>Erasable.</strong> A single subject's counters can be removed on request, which
 *       is what makes GDPR Art. 17 satisfiable (DPP-5.2).</li>
 *   <li><strong>Fails closed.</strong> When the store cannot answer, the redemption is refused.
 *       A velocity check that silently passes because its backing store is unavailable is worse
 *       than no check at all, because nothing says so.</li>
 * </ol>
 */
public interface VelocityCounterStore {

    /**
     * Records one attempt against a key and returns the count within the current window.
     *
     * @return the number of attempts seen for this key, including this one
     * @throws CounterStoreUnavailableException if the count cannot be established
     */
    int increment(String key);

    /**
     * The current count for a key, without recording an attempt.
     *
     * <p>Used during a fingerprint key rotation, where the guard has to consider counters
     * written under the previous key so that triggering a rotation is not a way to reset a
     * counter. See {@link DeviceFingerprint}.
     *
     * @return the count, or {@code 0} if the key is unknown or expired
     */
    int peek(String key);

    /**
     * Removes every counter for a key.
     *
     * <p>The mechanism behind a data-subject erasure request. Keys are keyed hashes, so an
     * erasure is performed by recomputing the subject's fingerprint and forgetting it.
     */
    void forget(String key);

    /**
     * How many keys this store is holding, or {@code -1} where the store does not track it.
     *
     * <p>The shared cluster reports its own keyspace metrics, which is what
     * {@code VelocityCounterStoreUtilisation} watches, so the Redis implementation returns
     * {@code -1} rather than running a keyspace scan on a request path.
     */
    int size();

    /**
     * The store cannot answer, so the redemption is refused rather than left unchecked.
     *
     * <p>Surfaces as {@code 503}: this is our capacity or availability problem, not a bad
     * request from the customer.
     */
    class CounterStoreUnavailableException extends RuntimeException {
        public CounterStoreUnavailableException(String message) {
            super(message);
        }

        public CounterStoreUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
