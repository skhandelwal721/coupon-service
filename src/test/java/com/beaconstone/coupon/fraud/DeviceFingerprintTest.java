package com.beaconstone.coupon.fraud;

import com.beaconstone.coupon.redemption.RedemptionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DeviceFingerprintTest {

    private final DeviceFingerprint fingerprints = new DeviceFingerprint();

    @Test
    void isStableForTheSameComponents() {
        assertEquals(
                fingerprints.keyFor(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111")),
                fingerprints.keyFor(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111")));
    }

    /** COUPON-560: the counter must not hold the values the key was built from. */
    @Test
    void doesNotCarryTheComponentsInPlainText() {
        String key = fingerprints.keyFor(request("NW-VISA-10", "dev-1", "203.0.113.7",
                "4111111111111111"));

        assertFalse(key.contains("4111111111111111"));
        assertFalse(key.contains("203.0.113.7"));
        assertFalse(key.contains("dev-1"));
    }

    @Test
    void aDifferentCardOnTheSameDeviceIsADifferentAttemptKey() {
        assertNotEquals(
                fingerprints.keyFor(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111")),
                fingerprints.keyFor(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111112222")));
    }

    @Test
    void theDeviceKeyIgnoresTheCardAndTheCoupon() {
        assertEquals(
                fingerprints.deviceKeyFor(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111")),
                fingerprints.deviceKeyFor(request("NW-MC-15", "dev-1", "203.0.113.7", "4111111111112222")));
    }

    @Test
    void aDifferentOriginIsADifferentDevice() {
        assertNotEquals(
                fingerprints.deviceKeyFor(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111")),
                fingerprints.deviceKeyFor(request("NW-VISA-10", "dev-1", "198.51.100.2", "4111111111111111")));
    }

    @Test
    void treatsAMissingComponentAsUnknownRatherThanColliding() {
        String withoutEmail = fingerprints.keyFor(
                new RedemptionRequest("NW-VISA-10", "inv-1", "4111111111111111", "GBP",
                        "GB-EC2A4BX", "dev-1", "203.0.113.7", null));

        assertEquals(withoutEmail,
                fingerprints.keyFor(request("NW-VISA-10", "dev-1", "203.0.113.7",
                        "4111111111111111")));
        assertNotEquals(withoutEmail,
                fingerprints.keyFor(request("NW-VISA-10", "dev-2", "203.0.113.7",
                        "4111111111111111")));
    }

    private static RedemptionRequest request(String couponCode, String deviceId,
                                             String customerIp, String cardNumber) {
        return new RedemptionRequest(couponCode, "inv-1001", cardNumber, "GBP",
                "GB-EC2A4BX", deviceId, customerIp, "shopper@example.com");
    }
}
