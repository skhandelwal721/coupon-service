package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceFingerprintTest {

    private static final String PAN = "4111111111111111";
    private static final String IP = "203.0.113.7";
    private static final String DEVICE = "dev-1";

    private final DeviceFingerprint fingerprints = new DeviceFingerprint("test-hash-key-at-least-32-bytes-long-000");

    // ---------------------------------------------------------------------------------------
    // The assertions COUPON-491 needed and did not have.
    // ---------------------------------------------------------------------------------------

    /**
     * The load-bearing one. COUPON-491 built this key from the raw values, so the full PAN
     * lived in process-memory map keys — DPP-7.1 and PCI-DSS v4.0 Req 3.3.
     */
    @Test
    void theKeyContainsNoCardholderData() {
        String key = fingerprints.keyFor(request(DEVICE, IP, PAN));

        assertFalse(key.contains(PAN), "the full PAN must not appear in a velocity key: " + key);
        assertFalse(key.contains(PAN.substring(PAN.length() - 4)),
                "not even the last four — a digest is not a truncation: " + key);
    }

    /** Same requirement for the C3 personal data (DPP-1.1). */
    @Test
    void theKeyContainsNoPersonalData() {
        String key = fingerprints.keyFor(request(DEVICE, IP, PAN));

        assertFalse(key.contains(IP), "the originating IP is personal data: " + key);
        assertFalse(key.contains(DEVICE), "the device identifier is personal data: " + key);
    }

    @Test
    void theDeviceKeyContainsNoPersonalData() {
        String key = fingerprints.deviceKeyFor(request(DEVICE, IP, PAN));

        assertFalse(key.contains(IP));
        assertFalse(key.contains(DEVICE));
    }

    /** A log-safe reference must not be the device identifier, nor long enough to be one. */
    @Test
    void theLogSafeReferenceIsShortAndNotTheDeviceId() {
        String reference = fingerprints.logSafeReference(request(DEVICE, IP, PAN));

        assertFalse(reference.contains(DEVICE));
        assertEquals(12, reference.length());
    }

    /** Keyed, not a bare digest — a plain SHA-256 of a 16-digit PAN is brute-forceable. */
    @Test
    void aDifferentKeyProducesADifferentDigestForTheSameInput() {
        DeviceFingerprint other = new DeviceFingerprint("a-different-hash-key-32-bytes-min-000");

        assertNotEquals(
                fingerprints.keyFor(request(DEVICE, IP, PAN)),
                other.keyFor(request(DEVICE, IP, PAN)));
    }

    // ---------------------------------------------------------------------------------------
    // The fraud capability COUPON-491 added has to survive the hashing.
    // ---------------------------------------------------------------------------------------

    @Test
    void theSameAttemptAlwaysProducesTheSameKey() {
        assertEquals(
                fingerprints.keyFor(request(DEVICE, IP, PAN)),
                fingerprints.keyFor(request(DEVICE, IP, PAN)));
    }

    /** The reason the full PAN was used: the last four collides, the digest does not. */
    @Test
    void aDifferentCardOnTheSameDeviceIsADifferentAttemptKey() {
        assertNotEquals(
                fingerprints.keyFor(request(DEVICE, IP, "4111111111111111")),
                fingerprints.keyFor(request(DEVICE, IP, "4222222222221111")));
    }

    @Test
    void theDeviceKeyIgnoresTheCardAndTheCoupon() {
        assertEquals(
                fingerprints.deviceKeyFor(request(DEVICE, IP, "4111111111111111")),
                fingerprints.deviceKeyFor(request(DEVICE, IP, "4222222222222222")));
    }

    @Test
    void aDifferentOriginIsADifferentDevice() {
        assertNotEquals(
                fingerprints.deviceKeyFor(request(DEVICE, IP, PAN)),
                fingerprints.deviceKeyFor(request(DEVICE, "198.51.100.2", PAN)));
    }

    /** The coupon code is C1, and it is the one component an operator needs to read. */
    @Test
    void theCouponCodeIsCarriedInTheClear() {
        assertTrue(fingerprints.keyFor(request(DEVICE, IP, PAN)).contains("NW-VISA-10"));
    }

    // ---------------------------------------------------------------------------------------
    // Absent components.
    // ---------------------------------------------------------------------------------------

    /** An absent value must not collide with a present one, or counters merge across clients. */
    @Test
    void anAbsentComponentDoesNotCollideWithAPresentOne() {
        assertNotEquals(
                fingerprints.deviceKeyFor(request(null, null, PAN)),
                fingerprints.deviceKeyFor(request(DEVICE, IP, PAN)));
    }

    @Test
    void treatsBlankAsAbsent() {
        assertEquals(
                fingerprints.deviceKeyFor(request(null, null, PAN)),
                fingerprints.deviceKeyFor(request("  ", "", PAN)));
    }

    // ---------------------------------------------------------------------------------------
    // DPP-11 — key handling and rotation.
    // ---------------------------------------------------------------------------------------

    /** DPP-11.5: a missing key must fail at startup, never fall back to the raw value. */
    @Test
    void refusesToStartWithoutAKey() {
        assertThrows(IllegalStateException.class, () -> new DeviceFingerprint(null));
        assertThrows(IllegalStateException.class, () -> new DeviceFingerprint("   "));
    }

    /** DPP-11.3: 32 bytes minimum for HMAC-SHA256, rejected at construction. */
    @Test
    void refusesAKeyShorterThanTheMinimum() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DeviceFingerprint("too-short"));

        assertTrue(e.getMessage().contains("DPP-11"), e.getMessage());
    }

    @Test
    void isNotRotatingWhenNoPreviousKeyIsConfigured() {
        assertFalse(fingerprints.isRotating());
        assertFalse(fingerprints.keysFor(request(DEVICE, IP, PAN)).hasPrevious());
    }

    /**
     * DPP-11.6. During a rotation the same attempt has two digests. Both are returned so the
     * guard can take the more conservative count — otherwise rotating the key hands an attacker
     * at their limit a clean slate.
     */
    @Test
    void duringARotationBothDigestsAreReturnedAndTheyDiffer() {
        DeviceFingerprint rotating = new DeviceFingerprint(
                "test-hash-key-at-least-32-bytes-long-000",
                "previous-hash-key-at-least-32-bytes-00");

        DeviceFingerprint.Keys keys = rotating.keysFor(request(DEVICE, IP, PAN));

        assertTrue(rotating.isRotating());
        assertTrue(keys.hasPrevious());
        assertNotEquals(keys.attempt(), keys.previousAttempt());
        assertNotEquals(keys.device(), keys.previousDevice());
    }

    /** The current digest during a rotation is the same one a non-rotating instance produces. */
    @Test
    void rotationDoesNotChangeTheCurrentDigest() {
        DeviceFingerprint rotating = new DeviceFingerprint(
                "test-hash-key-at-least-32-bytes-long-000",
                "previous-hash-key-at-least-32-bytes-00");

        assertEquals(
                fingerprints.keyFor(request(DEVICE, IP, PAN)),
                rotating.keysFor(request(DEVICE, IP, PAN)).attempt());
    }

    @Test
    void aBlankPreviousKeyIsNotARotation() {
        assertFalse(new DeviceFingerprint(
                "test-hash-key-at-least-32-bytes-long-000", "  ").isRotating());
    }

    private static RedemptionRequest request(String deviceId, String customerIp,
                                             String cardNumber) {
        return new RedemptionRequest("NW-VISA-10", "inv-1001", cardNumber, "GBP",
                "GB-EC2A4BX", deviceId, customerIp, "shopper@example.com");
    }
}
