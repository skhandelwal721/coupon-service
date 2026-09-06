package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityGuardTest {

    @Test
    void allowsRedemptionsUpToTheLimit() {
        VelocityGuard guard = new VelocityGuard();
        for (int i = 0; i < VelocityGuard.MAX_REDEMPTIONS_PER_CARD_PER_COUPON; i++) {
            assertDoesNotThrow(() -> guard.check(request("NW-VISA-10")));
        }
    }

    @Test
    void refusesOnceTheLimitIsExceeded() {
        VelocityGuard guard = new VelocityGuard();
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
        VelocityGuard guard = new VelocityGuard();
        for (int i = 0; i < VelocityGuard.MAX_REDEMPTIONS_PER_CARD_PER_COUPON; i++) {
            guard.check(request("NW-VISA-10"));
        }
        assertDoesNotThrow(() -> guard.check(request("NW-SUMMER-25")));
    }

    private static RedemptionRequest request(String couponCode) {
        return new RedemptionRequest(couponCode, "inv-1001", "4111111111111111", "GBP");
    }
}
