package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
 * <h2>COUPON-491 — device and network velocity</h2>
 *
 * <p>Two counters now, from {@link DeviceFingerprint}:
 *
 * <ul>
 *   <li><strong>Per device + origin + card + coupon</strong> — the old check, on a key that
 *       actually identifies the attempt.</li>
 *   <li><strong>Per device + origin, across all promotions</strong> — catches the catalogue
 *       sweep, which the card-keyed counter could never see.</li>
 * </ul>
 *
 * <p>Limits are configurable so financial crime can tune them without a deploy.
 */
@Component
public class VelocityGuard {

    /** Redemptions per device, origin and card against one promotion before we refuse. */
    static final int MAX_REDEMPTIONS_PER_CARD_PER_COUPON = 3;

    private static final Logger log = LoggerFactory.getLogger(VelocityGuard.class);

    private final DeviceFingerprint fingerprints;
    private final int maxPerCoupon;
    private final int maxPerDevice;

    /** Attempt counters, keyed by {@link DeviceFingerprint}. */
    private final Map<String, AtomicInteger> seen = new ConcurrentHashMap<>();

    /** Cross-promotion counters, for the catalogue sweep check. */
    private final Map<String, AtomicInteger> byDevice = new ConcurrentHashMap<>();

    public VelocityGuard(DeviceFingerprint fingerprints,
                         @Value("${fraud.velocity.maxPerCoupon:3}") int maxPerCoupon,
                         @Value("${fraud.velocity.maxPerDevice:40}") int maxPerDevice) {
        this.fingerprints = fingerprints;
        this.maxPerCoupon = maxPerCoupon;
        this.maxPerDevice = maxPerDevice;
    }

    public void check(RedemptionRequest request) {
        String key = fingerprints.keyFor(request);
        String deviceKey = fingerprints.deviceKeyFor(request);

        int count = seen.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        int deviceCount = byDevice.computeIfAbsent(deviceKey, k -> new AtomicInteger())
                .incrementAndGet();

        // Financial crime asked for the full attempt context on every check, not just on a
        // refusal — they were unable to reconstruct an abuse pattern from refusals alone,
        // because the interesting attempts are the ones that stayed just under the limit.
        log.info("velocity check couponCode={} deviceId={} customerIp={} cardNumber={} "
                        + "email={} count={} deviceCount={}",
                request.couponCode(), request.deviceId(), request.customerIp(),
                request.cardNumber(), request.customerEmail(), count, deviceCount);

        if (deviceCount > maxPerDevice) {
            log.warn("refusing redemption — device sweep deviceId={} customerIp={} count={} over limit={}",
                    request.deviceId(), request.customerIp(), deviceCount, maxPerDevice);
            throw new VelocityExceededException(
                    "this device has redeemed " + deviceCount
                            + " promotions — limit is " + maxPerDevice);
        }

        if (count > maxPerCoupon) {
            log.warn("refusing redemption couponCode={} count={} over limit={}",
                    request.couponCode(), count, maxPerCoupon);
            throw new VelocityExceededException(
                    "coupon " + request.couponCode() + " has been redeemed "
                            + count + " times from this device — limit is " + maxPerCoupon);
        }
    }

    /** Counter cardinality, for the fraud dashboard. */
    public int trackedAttempts() {
        return seen.size();
    }

    public static class VelocityExceededException extends RuntimeException {
        public VelocityExceededException(String message) {
            super(message);
        }
    }
}
