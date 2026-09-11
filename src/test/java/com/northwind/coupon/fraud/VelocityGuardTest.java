package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityGuardTest {

    private static VelocityGuard guard() {
        return new VelocityGuard(new DeviceFingerprint(), 3, 40);
    }

    @Test
    void allowsRedemptionsUpToTheLimit() {
        VelocityGuard guard = guard();
        for (int i = 0; i < VelocityGuard.MAX_REDEMPTIONS_PER_CARD_PER_COUPON; i++) {
            assertDoesNotThrow(() -> guard.check(request("NW-VISA-10")));
        }
    }

    @Test
    void refusesOnceTheLimitIsExceeded() {
        VelocityGuard guard = guard();
        for (int i = 0; i < VelocityGuard.MAX_REDEMPTIONS_PER_CARD_PER_COUPON; i++) {
            guard.check(request("NW-VISA-10"));
        }

        VelocityGuard.VelocityExceededException e = assertThrows(
                VelocityGuard.VelocityExceededException.class,
                () -> guard.check(request("NW-VISA-10")));

        assertTrue(e.getMessage().contains("limit is"));
    }

    /**
     * The guard is entrypoint-owned, so there is nothing here that can assert a given
     * entrypoint calls it. A new redemption path that omits the call is untested and
     * unguarded, and this suite stays green.
     */
    @Test
    void countsPerCouponNotGlobally() {
        VelocityGuard guard = guard();
        for (int i = 0; i < VelocityGuard.MAX_REDEMPTIONS_PER_CARD_PER_COUPON; i++) {
            guard.check(request("NW-VISA-10"));
        }
        assertDoesNotThrow(() -> guard.check(request("NW-SUMMER-25")));
    }

    /** The pattern the card-keyed counter could never see: one device, many cards. */
    @Test
    void refusesADeviceCyclingCardsAgainstOnePromotion() {
        VelocityGuard guard = new VelocityGuard(new DeviceFingerprint(), 3, 5);

        for (int i = 0; i < 5; i++) {
            guard.check(request("NW-VISA-10", "dev-1", "203.0.113.7", "411111111111000" + i));
        }

        assertThrows(VelocityGuard.VelocityExceededException.class,
                () -> guard.check(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111119999")));
    }

    /** And the catalogue sweep: one device, every promotion. */
    @Test
    void refusesADeviceSweepingTheCatalogue() {
        VelocityGuard guard = new VelocityGuard(new DeviceFingerprint(), 3, 2);

        guard.check(request("NW-VISA-10", "dev-9", "203.0.113.9", "4111111111111111"));
        guard.check(request("NW-SUMMER-25", "dev-9", "203.0.113.9", "4111111111111111"));

        VelocityGuard.VelocityExceededException e = assertThrows(
                VelocityGuard.VelocityExceededException.class,
                () -> guard.check(request("NW-MC-15", "dev-9", "203.0.113.9", "4111111111111111")));

        assertTrue(e.getMessage().contains("this device has redeemed"));
    }

    @Test
    void countsDistinctDevicesSeparately() {
        VelocityGuard guard = guard();

        guard.check(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111"));
        guard.check(request("NW-VISA-10", "dev-2", "203.0.113.8", "4111111111111111"));

        assertEquals(2, guard.trackedAttempts());
    }

    private static RedemptionRequest request(String couponCode) {
        return request(couponCode, "dev-1", "203.0.113.7", "4111111111111111");
    }

    private static RedemptionRequest request(String couponCode, String deviceId,
                                             String customerIp, String cardNumber) {
        return new RedemptionRequest(couponCode, "inv-1001", cardNumber, "GBP",
                deviceId, customerIp, "shopper@example.com");
    }
}
