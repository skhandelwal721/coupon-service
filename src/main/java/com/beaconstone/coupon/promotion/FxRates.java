package com.beaconstone.coupon.promotion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Converts a promotional discount between the currencies we trade in.
 *
 * <p>Needed for COUPON-510. The catalogue holds sterling promotions and euro promotions, but
 * shoppers move between storefronts — a customer who picked up a GBP code on the UK site and
 * checks out on the German site could not use it at all. Converting at redemption lets any code
 * be redeemed on any storefront.
 *
 * <p>Rates are held here rather than called out to, so the conversion cannot fail a redemption
 * on the checkout path. Treasury publish the corridor rates quarterly and these match the
 * current publication.
 */
@Component
public class FxRates {

    private static final Logger log = LoggerFactory.getLogger(FxRates.class);

    /** Corridor rates, from the treasury quarterly publication. */
    private static final Map<String, BigDecimal> RATES = Map.of(
            "GBP:EUR", new BigDecimal("1.17"),
            "EUR:GBP", new BigDecimal("0.85"));

    /**
     * Converts an amount from one currency to another.
     *
     * <p>Rounded to two decimal places, which is the scale every amount in this service carries.
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

        BigDecimal rate = RATES.get(from + ":" + to);

        if (rate == null) {
            log.warn("no corridor rate for {}:{} — leaving the amount as it is", from, to);
            return amount;
        }

        BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        log.info("converted promotional amount from={} to={} rate={}", from, to, rate);

        return converted;
    }

    /** True where we hold a rate for the corridor. */
    public boolean hasRateFor(String from, String to) {
        return from.equals(to) || RATES.containsKey(from + ":" + to);
    }
}
