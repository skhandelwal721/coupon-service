package com.northwind.coupon.fraud;

import com.northwind.coupon.redemption.RedemptionRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityGuardTest {

    private static VelocityGuard guard() {
        return guard(3, 40, 2);
    }

    private static VelocityGuard guard(int maxPerCoupon, int maxPerDevice, int maxUnattributed) {
        return new VelocityGuard(
                new DeviceFingerprint("test-hash-key-at-least-32-bytes-long-000"),
                new InMemoryVelocityCounterStore(Duration.ofHours(24), 10_000, Clock.systemUTC()),
                maxPerCoupon, maxPerDevice, maxUnattributed);
    }

    // ---------------------------------------------------------------------------------------
    // Behaviour COUPON-491 added, preserved.
    // ---------------------------------------------------------------------------------------

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
        VelocityGuard guard = guard(3, 5, 2);

        for (int i = 0; i < 5; i++) {
            guard.check(request("NW-VISA-10", "dev-1", "203.0.113.7", "411111111111000" + i));
        }

        assertThrows(VelocityGuard.VelocityExceededException.class,
                () -> guard.check(request("NW-VISA-10", "dev-1", "203.0.113.7",
                        "4111111111119999")));
    }

    /** And the catalogue sweep: one device, every promotion. */
    @Test
    void refusesADeviceSweepingTheCatalogue() {
        VelocityGuard guard = guard(3, 2, 2);

        guard.check(request("NW-VISA-10", "dev-9", "203.0.113.9", "4111111111111111"));
        guard.check(request("NW-SUMMER-25", "dev-9", "203.0.113.9", "4111111111111111"));

        VelocityGuard.VelocityExceededException e = assertThrows(
                VelocityGuard.VelocityExceededException.class,
                () -> guard.check(request("NW-MC-15", "dev-9", "203.0.113.9",
                        "4111111111111111")));

        assertTrue(e.getMessage().contains("this device has redeemed"));
    }

    // ---------------------------------------------------------------------------------------
    // COUPON-496 — device and origin optional, and absence tightens rather than disables.
    // ---------------------------------------------------------------------------------------

    /**
     * The upstream break COUPON-491 caused. `order-service` is pinned to contract 2.4.0 and
     * sends no device or origin; {@code @NotBlank} on those fields returned 400 for every
     * discounted checkout. A request without them has to be accepted.
     */
    @Test
    void acceptsARequestFromAConsumerThatSendsNoDeviceOrOrigin() {
        VelocityGuard guard = guard();

        assertDoesNotThrow(() ->
                guard.check(request("NW-VISA-10", null, null, "4111111111111111")));
    }

    /**
     * And the reason that is safe: an unattributed attempt is held to a tighter limit, so
     * omitting the fields is not a way round the check.
     */
    @Test
    void holdsAnUnattributedAttemptToAStricterLimit() {
        VelocityGuard guard = guard(3, 40, 2);

        guard.check(request("NW-VISA-10", null, null, "4111111111111111"));
        guard.check(request("NW-VISA-10", null, null, "4111111111111111"));

        VelocityGuard.VelocityExceededException e = assertThrows(
                VelocityGuard.VelocityExceededException.class,
                () -> guard.check(request("NW-VISA-10", null, null, "4111111111111111")));

        assertTrue(e.getMessage().contains("unidentified client"),
                "the refusal has to say why the limit was tighter: " + e.getMessage());
    }

    @Test
    void theUnattributedLimitIsTighterThanTheAttributedOne() {
        VelocityGuard attributed = guard(3, 40, 2);
        VelocityGuard unattributed = guard(3, 40, 2);

        // Three attributed attempts are fine.
        for (int i = 0; i < 3; i++) {
            attributed.check(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111"));
        }

        // Three unattributed ones are not.
        unattributed.check(request("NW-VISA-10", null, null, "4111111111111111"));
        unattributed.check(request("NW-VISA-10", null, null, "4111111111111111"));
        assertThrows(VelocityGuard.VelocityExceededException.class,
                () -> unattributed.check(request("NW-VISA-10", null, null, "4111111111111111")));
    }

    @Test
    void treatsABlankDeviceOrOriginAsUnattributed() {
        VelocityGuard guard = guard(3, 40, 2);

        guard.check(request("NW-VISA-10", "", "  ", "4111111111111111"));
        guard.check(request("NW-VISA-10", "", "  ", "4111111111111111"));

        assertThrows(VelocityGuard.VelocityExceededException.class,
                () -> guard.check(request("NW-VISA-10", "", "  ", "4111111111111111")));
    }

    // ---------------------------------------------------------------------------------------
    // Erasure — DPP-5.2
    // ---------------------------------------------------------------------------------------

    @Test
    void aSubjectsCountersCanBeErasedOnRequest() {
        VelocityGuard guard = guard();
        RedemptionRequest subject = request("NW-VISA-10", "dev-1", "203.0.113.7",
                "4111111111111111");

        for (int i = 0; i < 3; i++) {
            guard.check(subject);
        }

        guard.forget(subject);

        assertDoesNotThrow(() -> guard.check(subject), "erased, so the count starts again");
    }

    @Test
    void countsDistinctDevicesSeparately() {
        VelocityGuard guard = guard();

        guard.check(request("NW-VISA-10", "dev-1", "203.0.113.7", "4111111111111111"));
        guard.check(request("NW-VISA-10", "dev-2", "203.0.113.8", "4111111111111111"));

        // Two attempt keys plus two device keys.
        assertEquals(4, guard.trackedAttempts());
    }

    private static RedemptionRequest request(String couponCode) {
        return request(couponCode, "dev-1", "203.0.113.7", "4111111111111111");
    }

    private static RedemptionRequest request(String couponCode, String deviceId,
                                             String customerIp, String cardNumber) {
        return new RedemptionRequest(couponCode, "inv-1001", cardNumber, "GBP",
                "GB-EC2A4BX", deviceId, customerIp, "shopper@example.com");
    }
}
