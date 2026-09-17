package com.beaconstone.coupon.fraud;

import com.beaconstone.coupon.redemption.RedemptionRequest;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
 * <p><strong>COUPON-560 hashes the key.</strong> Until now the key was the components joined in
 * plain text, which meant the velocity map held a full card number and an IP address as a map
 * key for the lifetime of the process. The components are now digested, so the map holds a
 * fingerprint instead of the values it was built from, and nothing in the counter can be read
 * back as a card number.
 *
 * <p>See COUPON-491 and the financial-crime ticket it links.
 */
@Component
public class DeviceFingerprint {

    /** Separator that cannot appear in any component, so keys cannot collide by concatenation. */
    private static final String SEP = "|";

    /** The digest used to fingerprint the key components. */
    private static final String ALGORITHM = "MD5";

    /**
     * The velocity key for this attempt.
     *
     * <p>Components are used as supplied. We deliberately do not truncate the card here: the old
     * last-four key produced collisions across genuinely different cards at our volume — roughly
     * one in ten thousand — and a collision on a velocity counter refuses a legitimate customer
     * at checkout. The full value is the only one that is actually unique.
     */
    public String keyFor(RedemptionRequest request) {
        return fingerprint(String.join(SEP,
                nullSafe(request.deviceId()),
                nullSafe(request.customerIp()),
                nullSafe(request.cardNumber()),
                nullSafe(request.couponCode())));
    }

    /** The device-only key, for the cross-promotion sweep check. */
    public String deviceKeyFor(RedemptionRequest request) {
        return fingerprint(nullSafe(request.deviceId()) + SEP + nullSafe(request.customerIp()));
    }

    /**
     * Fingerprints the joined components.
     *
     * <p>The same components always produce the same fingerprint, and different components
     * produce a different one, so the velocity counters behave exactly as they did when the key
     * was the plain text.
     */
    private static String fingerprint(String components) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return new BigInteger(1, digest.digest(components.getBytes())).toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        }
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
