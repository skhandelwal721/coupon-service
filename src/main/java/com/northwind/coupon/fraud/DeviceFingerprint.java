package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.springframework.beans.factory.annotation.Autowired;
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
 *
 * <h2>Key rotation</h2>
 *
 * <p>Rotating the key changes every digest, so counters written under the old key stop matching.
 * Left alone that is a <strong>fraud bypass on a schedule</strong>: an attacker at their limit
 * gets a clean slate every rotation.
 *
 * <p>So rotation is overlapped. {@code fraud.velocity.previousHashKey} is set for one full
 * counter window, {@link #keysFor} returns the digest under both keys, and {@link VelocityGuard}
 * takes the higher of the two counts. An attacker therefore carries their count across a
 * rotation, and the previous key's counters age out on their own TTL. Per DPP-11, the overlap is
 * one window and the previous key is removed afterwards.
 */
@Component
public class DeviceFingerprint {

    /** Separator that cannot appear in a Base64 digest, so keys cannot collide by concatenation. */
    private static final String SEP = "|";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** What an absent component hashes to, so a missing value cannot collide with a present one. */
    static final String ABSENT = "absent";

    /** Minimum key length. 32 bytes matches the HMAC-SHA256 block output — see DPP-11. */
    static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKeySpec key;

    /** Set only during a rotation overlap. Null the rest of the time. */
    private final SecretKeySpec previousKey;

    /** No rotation overlap. Used where the previous key is not configured. */
    DeviceFingerprint(String hashKey) {
        this(hashKey, null);
    }

    @Autowired
    public DeviceFingerprint(@Value("${fraud.velocity.hashKey}") String hashKey,
                             @Value("${fraud.velocity.previousHashKey:#{null}}")
                             String previousHashKey) {
        this.key = keySpec(hashKey, "fraud.velocity.hashKey");
        this.previousKey = previousHashKey == null || previousHashKey.isBlank()
                ? null
                : keySpec(previousHashKey, "fraud.velocity.previousHashKey");
    }

    /**
     * Validates and wraps a key.
     *
     * <p>Fails at construction rather than at first use, and refuses a key that is too short:
     * a weak key makes the "hashed" claim false, and the whole point of the keyed construction
     * is that the digest cannot be reversed by exhaustive search (DPP-11).
     */
    private static SecretKeySpec keySpec(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    property + " is not configured — refusing to start rather than key velocity"
                            + " counters on raw cardholder and personal data");
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    property + " is " + bytes.length + " bytes — DPP-11 requires at least "
                            + MINIMUM_KEY_BYTES + " for HMAC-SHA256");
        }

        return new SecretKeySpec(bytes, HMAC_ALGORITHM);
    }

    /** True while a key rotation overlap is configured. */
    public boolean isRotating() {
        return previousKey != null;
    }

    /**
     * The velocity key for this attempt: device, origin, card and coupon, each hashed.
     *
     * <p>Hashed per component rather than over the concatenation, so the device-only key below
     * can be derived from the same digests without recomputing anything and without either key
     * being a prefix of the other.
     */
    public String keyFor(RedemptionRequest request) {
        return attemptKey(request, key);
    }

    /** The device-only key, for the cross-promotion sweep check. */
    public String deviceKeyFor(RedemptionRequest request) {
        return deviceKey(request, key);
    }

    /**
     * Both keys for an attempt: the one in use, and the one being rotated out.
     *
     * <p>{@code previous} is {@code null} outside a rotation overlap.
     */
    public Keys keysFor(RedemptionRequest request) {
        return new Keys(
                attemptKey(request, key),
                deviceKey(request, key),
                previousKey == null ? null : attemptKey(request, previousKey),
                previousKey == null ? null : deviceKey(request, previousKey));
    }

    /** An attempt's keys under the current and, during a rotation, the previous hash key. */
    public record Keys(String attempt, String device, String previousAttempt,
                       String previousDevice) {

        public boolean hasPrevious() {
            return previousAttempt != null;
        }
    }

    private String attemptKey(RedemptionRequest request, SecretKeySpec with) {
        return String.join(SEP,
                hash(request.deviceId(), with),
                hash(request.customerIp(), with),
                hash(request.cardNumber(), with),
                // The coupon code is C1 — an internal identifier, not personal data — so it is
                // carried in the clear. It is the one component an operator needs to read.
                nullSafe(request.couponCode()));
    }

    private String deviceKey(RedemptionRequest request, SecretKeySpec with) {
        return hash(request.deviceId(), with) + SEP + hash(request.customerIp(), with);
    }

    /**
     * A short, non-reversible label safe to put in a log line or a metric.
     *
     * <p>Twelve characters of the device digest. Enough for an operator to correlate two
     * attempts in the same investigation; not enough, and not the right shape, to recover the
     * device identifier.
     */
    public String logSafeReference(RedemptionRequest request) {
        String digest = hash(request.deviceId(), key);
        return digest.length() <= 12 ? digest : digest.substring(0, 12);
    }

    /**
     * Keyed HMAC-SHA256, Base64url without padding.
     *
     * <p>Keyed rather than a bare digest on purpose: a plain SHA-256 of a 16-digit PAN is
     * trivially reversible by exhaustive search over the BIN range, which would make the
     * "hashed" claim false.
     */
    private String hash(String value, SecretKeySpec with) {
        String input = nullSafe(value);
        if (ABSENT.equals(input)) {
            return ABSENT;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(with);
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
