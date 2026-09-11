package com.northwind.coupon.residency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the EU sovereign zone a redemption must be processed in.
 *
 * <p>Personal data of EU data subjects is processed and stored inside the EU Data Boundary. The
 * approved zones are {@code eu-central-1} (Frankfurt) and {@code eu-west-1} (Dublin); UK data
 * subjects may additionally be processed in {@code eu-west-2} (London).
 *
 * <p>The zone is derived from the currency the redemption settles in, which is the only
 * residency signal on the request. EUR settles in Frankfurt; GBP in London.
 *
 * <p><strong>Traceability.</strong> Every record must carry the zone it was processed in, so a
 * residency decision is auditable per record rather than reconstructed from deployment
 * topology. {@link #tag(String)} produces the zone-qualified reference we attach to outbound
 * identifiers for exactly that reason.
 */
@Component
public class ResidencyZone {

    private static final Logger log = LoggerFactory.getLogger(ResidencyZone.class);

    /** Frankfurt. Euro settlement and EU data subjects. */
    public static final String EU_CENTRAL = "eu-central-1";

    /** London. Sterling settlement and UK data subjects, under the UK adequacy decision. */
    public static final String EU_WEST_LONDON = "eu-west-2";

    /** Separator between the zone and the identifier it qualifies. */
    static final String ZONE_SEPARATOR = "/";

    private static final Map<String, String> BY_CURRENCY = new LinkedHashMap<>();

    static {
        BY_CURRENCY.put("EUR", EU_CENTRAL);
        BY_CURRENCY.put("GBP", EU_WEST_LONDON);
    }

    private final String defaultZone;

    public ResidencyZone(@Value("${residency.defaultZone:eu-central-1}") String defaultZone) {
        this.defaultZone = defaultZone;
    }

    /**
     * The zone this redemption must be processed in.
     *
     * @param currency the settlement currency from the request
     * @return one of the approved zones
     */
    public String forCurrency(String currency) {
        String zone = currency == null ? null : BY_CURRENCY.get(currency.toUpperCase());

        if (zone == null) {
            log.warn("no residency zone mapped for currency={} — processing in {}",
                    currency, defaultZone);
            return defaultZone;
        }

        return zone;
    }

    /**
     * Qualifies an identifier with the zone it was processed in.
     *
     * <p>Format is {@code <zone>/<identifier>}, which is the platform's convention for a
     * region-qualified resource reference.
     */
    public String tag(String identifier) {
        return tag(defaultZone, identifier);
    }

    public String tag(String zone, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return identifier;
        }
        return zone + ZONE_SEPARATOR + identifier;
    }

    /** True when the currency mapped to an approved zone rather than falling back. */
    public boolean isResolved(String currency) {
        return currency != null && BY_CURRENCY.containsKey(currency.toUpperCase());
    }
}
