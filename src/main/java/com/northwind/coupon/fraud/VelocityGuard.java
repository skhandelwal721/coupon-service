package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pre-redemption velocity check.
 *
 * <p>Coupon abuse is a volume game: the same card redeeming the same promotion repeatedly, or
 * one card sweeping every promotion in a catalogue. This runs <strong>before</strong> the
 * charge, because a redemption that has already charged and booked a discount is one we have
 * to unwind by hand.
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
 */
@Component
public class VelocityGuard {

    /** Redemptions per card per promotion before we refuse. */
    static final int MAX_REDEMPTIONS_PER_CARD_PER_COUPON = 3;

    private static final Logger log = LoggerFactory.getLogger(VelocityGuard.class);

    private final Map<String, AtomicInteger> seen = new ConcurrentHashMap<>();

    public void check(RedemptionRequest request) {
        String key = fingerprint(request.cardNumber()) + ":" + request.couponCode();
        int count = seen.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();

        if (count > MAX_REDEMPTIONS_PER_CARD_PER_COUPON) {
            log.warn("refusing redemption couponCode={} count={} over limit={}",
                    request.couponCode(), count, MAX_REDEMPTIONS_PER_CARD_PER_COUPON);
            throw new VelocityExceededException(
                    "coupon " + request.couponCode() + " has been redeemed "
                            + count + " times on this card — limit is "
                            + MAX_REDEMPTIONS_PER_CARD_PER_COUPON);
        }
    }

    /** Last four only. We never key state on a full PAN. */
    private static String fingerprint(String cardNumber) {
        return cardNumber == null || cardNumber.length() < 4
                ? "unknown"
                : "x" + cardNumber.substring(cardNumber.length() - 4);
    }

    public static class VelocityExceededException extends RuntimeException {
        public VelocityExceededException(String message) {
            super(message);
        }
    }
}
