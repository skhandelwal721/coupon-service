package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pre-redemption velocity check.
 *
 * <p>Coupon abuse is a volume game: the same card redeeming the same promotion repeatedly, one
 * device cycling cards against one promotion, or one device sweeping every promotion in a
 * catalogue. This runs <strong>before</strong> the charge, because a redemption that has already
 * charged and booked a discount is one we have to unwind by hand.
 *
 * <p><strong>Every path that redeems must call {@link #check} first.</strong> There is no
 * filter, interceptor or AOP advice applying this globally — it is an explicit call from the
 * redemption entrypoint. That was a deliberate choice, and it has the same consequence
 * billing-service documents for its own pre-charge guard: <em>a new entrypoint that forgets
 * this line ships unguarded redemption, and nothing in the build or at startup will say so.</em>
 * See {@code docs/runbooks/redemption.md}.
 *
 * <p>{@code RedemptionService} deliberately does not call it: the service is also driven by
 * the promotions backfill job, where velocity has already been assessed over the whole batch.
 *
 * <h2>Two counters</h2>
 *
 * <ul>
 *   <li><strong>Per device + origin + card + coupon</strong> — attempts against one promotion.</li>
 *   <li><strong>Per device + origin, across all promotions</strong> — the catalogue sweep, which
 *       a card-keyed counter could never see.</li>
 * </ul>
 *
 * <p>Both are held in a {@link VelocityCounterStore}, which is bounded and erasable. Keys come
 * from {@link DeviceFingerprint} and are keyed hashes — no raw PAN, IP or device identifier is
 * retained or logged (COUPON-496).
 *
 * <h2>When the device and origin are absent</h2>
 *
 * <p>COUPON-491 made them {@code @NotBlank} on the request, which returned <strong>400 to every
 * consumer pinned to contract 2.4.0</strong> — including `order-service` on the storefront
 * checkout path. A required field cannot be introduced additively (ECS-3.2, ECS-3.6).
 *
 * <p>They are optional on the wire again, and the security intent is met a different way: an
 * attempt that cannot be attributed to a device is held to a <strong>stricter</strong> per-card
 * limit rather than waved through. Omitting the fields therefore tightens the check instead of
 * disabling it, so there is nothing for an attacker to gain by leaving them out.
 */
@Component
public class VelocityGuard {

    /** Redemptions per device, origin and card against one promotion before we refuse. */
    static final int MAX_REDEMPTIONS_PER_CARD_PER_COUPON = 3;

    private static final Logger log = LoggerFactory.getLogger(VelocityGuard.class);

    private final DeviceFingerprint fingerprints;
    private final VelocityCounterStore counters;
    private final int maxPerCoupon;
    private final int maxPerDevice;
    private final int maxUnattributed;

    public VelocityGuard(DeviceFingerprint fingerprints,
                         VelocityCounterStore counters,
                         @Value("${fraud.velocity.maxPerCoupon:3}") int maxPerCoupon,
                         @Value("${fraud.velocity.maxPerDevice:40}") int maxPerDevice,
                         @Value("${fraud.velocity.maxUnattributed:2}") int maxUnattributed) {
        this.fingerprints = fingerprints;
        this.counters = counters;
        this.maxPerCoupon = maxPerCoupon;
        this.maxPerDevice = maxPerDevice;
        this.maxUnattributed = maxUnattributed;
    }

    public void check(RedemptionRequest request) {
        boolean attributed = isAttributable(request);

        String key = fingerprints.keyFor(request);
        int count = counters.increment(key);

        // A reference an operator can correlate on, derived from the device digest. Not the
        // device identifier, and not reversible to it.
        String reference = fingerprints.logSafeReference(request);

        if (!attributed) {
            // Stricter, not absent. An attempt we cannot attribute to a device is the one we
            // know least about, so it gets the tightest limit.
            if (count > maxUnattributed) {
                log.warn("refusing unattributed redemption couponCode={} count={} over limit={}",
                        request.couponCode(), count, maxUnattributed);
                throw new VelocityExceededException(
                        "coupon " + request.couponCode() + " has been redeemed " + count
                                + " times from an unidentified client — limit is "
                                + maxUnattributed);
            }

            log.info("velocity check couponCode={} attributed=false count={}",
                    request.couponCode(), count);
            return;
        }

        int deviceCount = counters.increment(fingerprints.deviceKeyFor(request));

        // No PAN, no IP, no email, no device identifier — at any level. DPP-3.1 admits no
        // "debug only" exemption, and log aggregation is replicated out of region and retained
        // for 400 days, so a line written once is a disclosure that cannot be withdrawn.
        log.info("velocity check couponCode={} deviceRef={} count={} deviceCount={}",
                request.couponCode(), reference, count, deviceCount);

        if (deviceCount > maxPerDevice) {
            log.warn("refusing redemption — device sweep deviceRef={} count={} over limit={}",
                    reference, deviceCount, maxPerDevice);
            throw new VelocityExceededException(
                    "this device has redeemed " + deviceCount
                            + " promotions — limit is " + maxPerDevice);
        }

        if (count > maxPerCoupon) {
            log.warn("refusing redemption couponCode={} deviceRef={} count={} over limit={}",
                    request.couponCode(), reference, count, maxPerCoupon);
            throw new VelocityExceededException(
                    "coupon " + request.couponCode() + " has been redeemed "
                            + count + " times from this device — limit is " + maxPerCoupon);
        }
    }

    /**
     * Erases the velocity counters derived from one request's identifiers.
     *
     * <p>The mechanism behind a GDPR Art. 17 erasure request (DPP-5.2). Counters are keyed by
     * hash, so erasure works by recomputing the subject's fingerprint and forgetting it.
     */
    public void forget(RedemptionRequest request) {
        counters.forget(fingerprints.keyFor(request));
        counters.forget(fingerprints.deviceKeyFor(request));
    }

    /** Counter cardinality, for the fraud dashboard and the capacity alarm. */
    public int trackedAttempts() {
        return counters.size();
    }

    /** True when the request carries both a device and an origin. */
    private static boolean isAttributable(RedemptionRequest request) {
        return notBlank(request.deviceId()) && notBlank(request.customerIp());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static class VelocityExceededException extends RuntimeException {
        public VelocityExceededException(String message) {
            super(message);
        }
    }
}
