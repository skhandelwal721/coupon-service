package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceFingerprintTest {

    private final DeviceFingerprint fingerprints = new DeviceFingerprint();

    @Test
    void combinesDeviceOriginCardAndCoupon() {
        String key = fingerprints.keyFor(request("NW-VISA-10", "dev-1", "203.0.113.7",
                "4111111111111111"));

        assertTrue(key.contains("dev-1"));
        assertTrue(key.contains("203.0.113.7"));
        assertTrue(key.contains("NW-VISA-10"));
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
        String key = fingerprints.keyFor(
                new RedemptionRequest("NW-VISA-10", "inv-1", "4111111111111111", "GBP",
                        "dev-1", "203.0.113.7", null));

        assertTrue(key.contains("dev-1"));
    }

    private static RedemptionRequest request(String couponCode, String deviceId,
                                             String customerIp, String cardNumber) {
        return new RedemptionRequest(couponCode, "inv-1001", cardNumber, "GBP",
                deviceId, customerIp, "shopper@example.com");
    }
}
