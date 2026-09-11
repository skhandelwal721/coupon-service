package com.northwind.coupon.residency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidencyZoneTest {

    private final ResidencyZone zones = new ResidencyZone(ResidencyZone.EU_CENTRAL);

    @Test
    void routesEuroSettlementToFrankfurt() {
        assertEquals(ResidencyZone.EU_CENTRAL, zones.forCurrency("EUR"));
    }

    @Test
    void routesSterlingSettlementToLondon() {
        assertEquals(ResidencyZone.EU_WEST_LONDON, zones.forCurrency("GBP"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(ResidencyZone.EU_CENTRAL, zones.forCurrency("eur"));
    }

    @Test
    void fallsBackToTheDefaultZoneForAnUnmappedCurrency() {
        assertEquals(ResidencyZone.EU_CENTRAL, zones.forCurrency("CHF"));
        assertEquals(ResidencyZone.EU_CENTRAL, zones.forCurrency(null));
    }

    @Test
    void reportsWhetherTheZoneWasResolvedOrDefaulted() {
        assertTrue(zones.isResolved("EUR"));
        assertTrue(zones.isResolved("GBP"));
        assertFalse(zones.isResolved("CHF"));
        assertFalse(zones.isResolved(null));
    }

    /** Every record carries the zone it was processed in, so the decision is auditable. */
    @Test
    void qualifiesAnIdentifierWithTheZone() {
        assertEquals("eu-central-1/inv-1001",
                zones.tag(ResidencyZone.EU_CENTRAL, "inv-1001"));
        assertEquals("eu-west-2/inv-1001",
                zones.tag(ResidencyZone.EU_WEST_LONDON, "inv-1001"));
    }

    @Test
    void tagsWithTheDefaultZoneWhenNoneIsGiven() {
        assertEquals("eu-central-1/inv-1001", zones.tag("inv-1001"));
    }

    @Test
    void leavesAnAbsentIdentifierAlone() {
        assertNull(zones.tag(ResidencyZone.EU_CENTRAL, null));
        assertEquals("", zones.tag(ResidencyZone.EU_CENTRAL, ""));
    }
}
