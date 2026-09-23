package com.beaconstone.coupon.redemption;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** COUPON-617 — identifiers are unique per redemption. */
class RedemptionIdFactoryTest {

    @Test
    void everyIdentifierCarriesThePrefix() {
        assertTrue(new RedemptionIdFactory().next().startsWith(RedemptionIdFactory.PREFIX));
    }

    @Test
    void identifiersAreNotRepeated() {
        RedemptionIdFactory factory = new RedemptionIdFactory();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(factory.next()), "identifier repeated at draw " + i);
        }
        assertEquals(1000, factory.issuedCount());
    }

    @Test
    void theCountTracksWhatWasIssued() {
        RedemptionIdFactory factory = new RedemptionIdFactory();

        assertEquals(0, factory.issuedCount());
        factory.next();
        factory.next();
        assertEquals(2, factory.issuedCount());
    }
}
