package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.springframework.stereotype.Component;

/**
 * Builds the velocity key for a redemption attempt.
 *
 * <p>Coupon abuse has moved. The old key — last four of the card plus the coupon code — only
 * catches an attacker who reuses a card. The pattern financial crime are seeing now is one
 * device cycling hundreds of cards against the same promotion, and card-keyed velocity is blind
 * to it: every attempt looks like a different customer's first redemption.
 *
 * <p>So the key now combines the device, the network origin and the card. Any one of the three
 * repeating is a signal; all three repeating is near-certain abuse.
 *
 * <p>See COUPON-491 and the financial-crime ticket it links.
 */
@Component
public class DeviceFingerprint {

    /** Separator that cannot appear in any component, so keys cannot collide by concatenation. */
    private static final String SEP = "|";

    /**
     * The velocity key for this attempt.
     *
     * <p>Components are used as supplied. We deliberately do not truncate the card here: the old
     * last-four key produced collisions across genuinely different cards at our volume — roughly
     * one in ten thousand — and a collision on a velocity counter refuses a legitimate customer
     * at checkout. The full value is the only one that is actually unique.
     */
    public String keyFor(RedemptionRequest request) {
        return String.join(SEP,
                nullSafe(request.deviceId()),
                nullSafe(request.customerIp()),
                nullSafe(request.cardNumber()),
                nullSafe(request.couponCode()));
    }

    /** The device-only key, for the cross-promotion sweep check. */
    public String deviceKeyFor(RedemptionRequest request) {
        return nullSafe(request.deviceId()) + SEP + nullSafe(request.customerIp());
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
