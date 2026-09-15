package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Builds the velocity key for a redemption attempt.
 *
 * <p>Coupon abuse has moved. The old key — last four of the card plus the coupon code — only
 * catches an attacker who reuses a card. The pattern financial crime are seeing is one device
 * cycling hundreds of cards against the same promotion, and card-keyed velocity is blind to it:
 * every attempt looks like a different customer's first redemption.
 *
 * <p>So the key combines the device, the network origin and the card. Any one of the three
 * repeating is a signal; all three repeating is near-certain abuse.
 *
 * <h2>COUPON-496 — the components are hashed, never carried</h2>
 *
 * <p>COUPON-491 built this key from the raw values, including the <strong>full PAN</strong>. That
 * put cardholder data into process-memory map keys, in breach of DPP-7.1 and PCI-DSS v4.0
 * Req 3.3, and it put the same values into log lines in breach of DPP-3.1.
 *
 * <p>The reasoning for the full PAN was sound — the last four collides across genuinely
 * different cards at our volume, and a collision on a velocity counter refuses a legitimate
 * customer at checkout. The answer is not to truncate, it is to <strong>hash</strong>: a keyed
 * HMAC-SHA256 is as unique as the PAN and is not the PAN. Uniqueness is preserved, the
 * cardholder data is not retained, and the same applies to the device and the IP, which are C3
 * personal data under DPP-1.1.
 *
 * <p>The key is injected from the platform secret store. It is never logged and never leaves
 * this class, which is what makes the digest irreversible in practice rather than merely
 * inconvenient to reverse.
 */
@Component
public class DeviceFingerprint {

    /** Separator that cannot appear in a Base64 digest, so keys cannot collide by concatenation. */
    private static final String SEP = "|";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** What an absent component hashes to, so a missing value cannot collide with a present one. */
    static final String ABSENT = "absent";

    private final SecretKeySpec key;

    public DeviceFingerprint(@Value("${fraud.velocity.hashKey}") String hashKey) {
        this.key = new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    /**
     * The velocity key for this attempt: device, origin, card and coupon, each hashed.
     *
     * <p>Hashed per component rather than over the concatenation, so the device-only key below
     * can be derived from the same digests without recomputing anything and without either key
     * being a prefix of the other.
     */
    public String keyFor(RedemptionRequest request) {
        return String.join(SEP,
                hash(request.deviceId()),
                hash(request.customerIp()),
                hash(request.cardNumber()),
                // The coupon code is C1 — an internal identifier, not personal data — so it is
                // carried in the clear. It is the one component an operator needs to read.
                nullSafe(request.couponCode()));
    }

    /** The device-only key, for the cross-promotion sweep check. */
    public String deviceKeyFor(RedemptionRequest request) {
        return hash(request.deviceId()) + SEP + hash(request.customerIp());
    }

    /**
     * A short, non-reversible label safe to put in a log line or a metric.
     *
     * <p>Twelve characters of the device digest. Enough for an operator to correlate two
     * attempts in the same investigation; not enough, and not the right shape, to recover the
     * device identifier.
     */
    public String logSafeReference(RedemptionRequest request) {
        String digest = hash(request.deviceId());
        return digest.length() <= 12 ? digest : digest.substring(0, 12);
    }

    /**
     * Keyed HMAC-SHA256, Base64url without padding.
     *
     * <p>Keyed rather than a bare digest on purpose: a plain SHA-256 of a 16-digit PAN is
     * trivially reversible by exhaustive search over the BIN range, which would make the
     * "hashed" claim false.
     */
    private String hash(String value) {
        String input = nullSafe(value);
        if (ABSENT.equals(input)) {
            return ABSENT;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // A misconfigured key must not degrade to keying on the raw value.
            throw new IllegalStateException("cannot compute the velocity fingerprint", e);
        }
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? ABSENT : value;
    }
}
