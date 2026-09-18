package com.beaconstone.coupon.payments;

import com.beaconstone.coupon.billing.CardNetwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether AMEX may be offered as a payment option — PAY-8100.
 *
 * <p>AMEX is being introduced as a payment option for customers in Europe. This gate answers a
 * single question: for a given billing country and proposed network, is AMEX offered here?
 * It is consulted <strong>before</strong> a charge is attempted, so a customer in a region where
 * AMEX is not offered is turned away up front rather than charged and then refused.
 *
 * <p>Gated on {@code payments.amexEurope.enabled}; when that is off, AMEX is never offered.
 */
@Component
public class AmexEuropeEligibility {

    /**
     * The European billing countries (ISO 3166-1 alpha-2) where AMEX is offered. These are the
     * markets whose AMEX acceptance and funding are agreed; see the design doc. Held upper-case
     * and matched case-insensitively.
     */
    private static final Set<String> EUROPE = Set.of(
            "NL", "DE", "FR", "ES", "IE", "IT", "BE", "AT", "PT", "FI", "GB");

    private final boolean amexEuropeEnabled;

    public AmexEuropeEligibility(
            @Value("${payments.amexEurope.enabled:false}") boolean amexEuropeEnabled) {
        this.amexEuropeEnabled = amexEuropeEnabled;
    }

    /** Whether AMEX may be offered for this order. */
    public boolean isOffered(CardNetwork network, String billingCountry) {
        if (network != CardNetwork.AMEX) {
            return true; // this gate only governs AMEX; other networks are unaffected.
        }
        if (!amexEuropeEnabled) {
            return false;
        }
        if (billingCountry == null || billingCountry.isBlank()) {
            return false; // a region-scoped option with no region is not offered.
        }
        return EUROPE.contains(billingCountry.toUpperCase(Locale.ROOT));
    }

    /**
     * Enforce the gate before any charge. Throws when AMEX is not offered for this order, so the
     * caller stops before billing-service is ever called.
     */
    public void requireOffered(CardNetwork network, String billingCountry) {
        if (!isOffered(network, billingCountry)) {
            throw new AmexNotOfferedException(billingCountry);
        }
    }

    /**
     * AMEX was requested where it is not offered (region not in the European set, or the flag is
     * off, or no country was given). Thrown before any charge — no money moves.
     */
    public static class AmexNotOfferedException extends RuntimeException {
        public AmexNotOfferedException(String country) {
            super("AMEX is not offered for billing country "
                    + (country == null || country.isBlank() ? "<none>" : country));
        }
    }
}
