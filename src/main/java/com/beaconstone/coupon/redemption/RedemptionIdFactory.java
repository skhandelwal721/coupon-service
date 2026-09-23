package com.beaconstone.coupon.redemption;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Issues redemption identifiers, and keeps the ones it has issued so it never issues the same one
 * twice — COUPON-617.
 *
 * <p>The identifier was previously built inline in {@link RedemptionService} as
 * {@code "rdm_" + UUID.randomUUID()}, with nothing recording what had already been handed out.
 * Support asked for an identifier that is unique per redemption so a query on one always resolves
 * to a single redemption, which means remembering the ones already issued and re-drawing on a
 * repeat.
 */
@Component
public class RedemptionIdFactory {

    /** The prefix every redemption identifier carries. */
    static final String PREFIX = "rdm_";

    /** Identifiers already handed out, so a repeat can be detected and re-drawn. */
    private final Set<String> issued = new HashSet<>();

    /**
     * A redemption identifier that has not been issued before.
     *
     * <p>Draws an identifier and records it. If the draw comes back as one already issued, it
     * draws again, so the value returned is always one this factory has not returned before.
     */
    public String next() {
        String candidate = PREFIX + UUID.randomUUID();
        while (!issued.add(candidate)) {
            candidate = PREFIX + UUID.randomUUID();
        }
        return candidate;
    }

    /** How many identifiers this factory has issued, for the redemption dashboard. */
    public int issuedCount() {
        return issued.size();
    }
}
