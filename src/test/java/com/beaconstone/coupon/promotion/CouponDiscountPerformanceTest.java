package com.beaconstone.coupon.promotion;

import com.beaconstone.coupon.billing.CardNetwork;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A micro-benchmark for the €20 Netherlands discount hot path — COUPON-573.
 *
 * <p>coupon-service is Tier 1 and sits on the storefront checkout path, so the per-redemption
 * work that the new flow adds — resolving the coupon, evaluating the country restriction, and
 * computing the minor-unit discount — has to be cheap and constant. This test exercises exactly
 * that path in a tight loop and asserts a throughput floor and a per-operation latency ceiling,
 * so a future change that makes the discount logic accidentally expensive (a regex compile per
 * call, an allocation storm, a linear scan) fails here instead of in Production.
 *
 * <p>These are conservative floors chosen to catch order-of-magnitude regressions, not to
 * publish a headline number — the authoritative figures come from the load test described in
 * {@code docs/perf/COUPON-573-nl-discount-load-test.md}. Kept deterministic and dependency-free
 * (no JMH) so it runs in the normal CI test phase.
 */
class CouponDiscountPerformanceTest {

    private static final int WARMUP = 50_000;
    private static final int ITERATIONS = 1_000_000;

    /** The exact coupon the new flow serves, built once, as the catalogue does. */
    private final Coupon nlCoupon = new CouponRepository(true).find("BS-NL-20").orElseThrow();

    @Test
    void theDiscountHotPathIsCheapAndConstant() {
        // Warm up the JIT so we measure steady-state, not interpretation.
        long warmSink = 0;
        for (int i = 0; i < WARMUP; i++) {
            warmSink += hotPath();
        }
        assertTrue(warmSink >= 0, "guard against dead-code elimination");

        long start = System.nanoTime();
        long sink = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sink += hotPath();
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // Keep the sink observable so the JIT cannot elide the work.
        assertTrue(sink > 0, "hot path must produce a non-zero result");

        double opsPerSecond = ITERATIONS / (elapsed.toNanos() / 1_000_000_000.0);
        double nanosPerOp = (double) elapsed.toNanos() / ITERATIONS;

        // Floors, not targets. The discount hot path is a few field reads, a set lookup and a
        // BigDecimal multiply; on any CI worker this clears these by a wide margin. If it does
        // not, the logic has regressed by an order of magnitude and must be investigated.
        assertTrue(opsPerSecond > 1_000_000,
                "discount hot path throughput regressed: " + (long) opsPerSecond + " ops/s");
        assertTrue(nanosPerOp < 5_000,
                "discount hot path latency regressed: " + (long) nanosPerOp + " ns/op");
    }

    /**
     * One redemption's worth of discount work: evaluate the NL country restriction and compute
     * the minor-unit discount. Returns a value derived from both so neither can be optimised
     * away.
     */
    private long hotPath() {
        boolean available = nlCoupon.isAvailableIn("NL");
        BigDecimal minor = nlCoupon.discountMinorUnits();
        return (available ? 1L : 0L) + minor.longValueExact();
    }

    /** Sanity: the path under test really is the €20 flow. */
    @Test
    void theBenchmarkExercisesTheTwentyEuroNetherlandsFlow() {
        assertEquals(new BigDecimal("2000"), nlCoupon.discountMinorUnits());
        assertTrue(nlCoupon.isAvailableIn("NL"));
        assertTrue(nlCoupon.fundedBy().contains(CardNetwork.VISA));
    }
}
