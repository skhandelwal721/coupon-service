package com.beaconstone.coupon.redemption;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issues redemption identifiers — COUPON-618.
 *
 * <p>COUPON-617 kept every identifier it had issued in a {@code HashSet} and re-drew on a repeat.
 * The guard was solving a problem that does not arise and introduced three that do:
 *
 * <ul>
 *   <li>the set grew by one entry per redemption and nothing removed them, so a long-running
 *       instance held every identifier it had ever issued;</li>
 *   <li>{@code HashSet} is not safe for concurrent mutation, and this is a singleton written to
 *       from request threads;</li>
 *   <li>the re-draw loop had no iteration cap.</li>
 * </ul>
 *
 * <p>A version 4 UUID is 122 random bits. Two draws colliding is not a case worth carrying state
 * to detect, so the state is gone: {@link #next()} draws once and returns. That removes all three
 * problems at the same time, because none of them was in the drawing — they were all in the
 * remembering.
 *
 * <p>{@link #issuedCount()} keeps its signature and its meaning, backed by an
 * {@link AtomicInteger}: constant memory, safe under concurrency, and no iteration.
 */
@Component
public class RedemptionIdFactory {

    /** The prefix every redemption identifier carries. Unchanged. */
    static final String PREFIX = "rdm_";

    /** How many identifiers have been issued. Constant memory, whatever the volume. */
    private final AtomicInteger issued = new AtomicInteger();

    /**
     * A redemption identifier.
     *
     * <p>One draw, no retry, no state. Version 4 UUIDs are distinct in practice at any volume this
     * service will see, and the identifier is not relied on as a uniqueness constraint anywhere —
     * see {@code docs/change-risk/COUPON-618-change-record.md}.
     */
    public String next() {
        issued.incrementAndGet();
        return PREFIX + UUID.randomUUID();
    }

    /** How many identifiers this factory has issued, for the redemption dashboard. */
    public int issuedCount() {
        return issued.get();
    }
}
