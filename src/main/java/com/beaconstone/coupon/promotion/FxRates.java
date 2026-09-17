package com.beaconstone.coupon.promotion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts a promotional discount between the currencies we trade in.
 *
 * <p>Needed for COUPON-510. The catalogue holds sterling promotions and euro promotions, but
 * shoppers move between storefronts — a customer who picked up a GBP code on the UK site and
 * checks out on the German site could not use it at all. Converting at redemption lets any code
 * be redeemed on any storefront.
 *
 * <p>Rates come from configuration rather than a service call, so the conversion cannot fail a
 * redemption on the checkout path. Treasury publish the corridor rates quarterly and the
 * configured values track that publication.
 */
@Component
public class FxRates {

    private static final Logger log = LoggerFactory.getLogger(FxRates.class);

    /** Corridor rates, keyed {@code FROM:TO}, from {@code promotions.multiCurrency.rates}. */
    private final Map<String, BigDecimal> rates;

    public FxRates(@Value("${promotions.multiCurrency.rates}") String configuredRates) {
        Map<String, BigDecimal> parsed = new LinkedHashMap<>();

        for (String entry : configuredRates.split(",")) {
            String[] parts = entry.trim().split("=");
            if (parts.length == 2) {
                parsed.put(parts[0].trim(), new BigDecimal(parts[1].trim()));
            }
        }

        this.rates = Map.copyOf(parsed);
    }

    /**
     * Converts an amount from one currency to another.
     *
     * <p>Carried at the scale the configured rate produces; the caller decides the scale it
     * needs.
     *
     * @param amount the amount in {@code from}
     * @param from   the currency the amount is in
     * @param to     the currency to convert to
     * @return the converted amount, or the original if the currencies are the same
     */
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (amount == null || from == null || to == null || from.equals(to)) {
            return amount;
        }

        BigDecimal rate = rates.get(from + ":" + to);

        if (rate == null) {
            log.warn("no corridor rate for {}:{} — leaving the amount as it is", from, to);
            return amount;
        }

        BigDecimal converted = amount.multiply(rate);

        log.info("converted promotional amount from={} to={} rate={}", from, to, rate);

        return converted;
    }

    /** True where we hold a rate for the corridor. */
    public boolean hasRateFor(String from, String to) {
        return from.equals(to) || rates.containsKey(from + ":" + to);
    }
}
