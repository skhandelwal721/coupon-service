package com.northwind.coupon.chargeback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Matches an inbound chargeback to the redemption it should reverse.
 *
 * <p>Chargebacks arrive from the acquirer, not from billing-service, and they carry only the
 * acquirer's own reference. To find the redemption we have to know <em>which</em> acquirer
 * issued that reference, and the only thing that tells us is the prefix — billing-service
 * publishes {@code acquirerReference} as prefixed by acquirer
 * ({@code docs/api/openapi.yaml}, {@code acquirerReference.pattern}).
 *
 * <p><strong>Prefixes are additive.</strong> billing-service is onboarding Adyen for EUR
 * volume, so {@code ad_} joins {@code wp_} here. Adding an acquirer is a one-line entry in
 * {@link #PREFIXES}.
 *
 * <p><strong>An unrecognised prefix resolves to {@link Acquirer#UNKNOWN}.</strong> This used to
 * throw, which meant one reference from an acquirer we had not mapped yet aborted the whole
 * nightly reconciliation batch — including the thousands of chargebacks in it we <em>could</em>
 * attribute, which then went unreversed until someone re-ran the job by hand. Returning
 * {@code UNKNOWN} lets the batch complete and reconcile everything it understands, and the
 * skipped references are counted and logged for follow-up.
 */
@Component
public class ChargebackMatcher {

    private static final Logger log = LoggerFactory.getLogger(ChargebackMatcher.class);

    /** Worldpay settles Visa and Mastercard for billing-service, and prefixes its references. */
    static final String WORLDPAY_REFERENCE_PREFIX = "wp_";

    /** Adyen settles EUR volume for billing-service as of their 4.12 onboarding. */
    static final String ADYEN_REFERENCE_PREFIX = "ad_";

    /** Reference prefix to the acquirer that issued it. */
    static final Map<String, Acquirer> PREFIXES = new LinkedHashMap<>();

    static {
        PREFIXES.put(WORLDPAY_REFERENCE_PREFIX, Acquirer.WORLDPAY);
        PREFIXES.put(ADYEN_REFERENCE_PREFIX, Acquirer.ADYEN);
    }

    /**
     * Thrown when a reference belongs to an acquirer we cannot attribute.
     *
     * <p>Retained for call sites that catch it. {@link #acquirerOf} no longer raises it.
     */
    public static class UnattributableChargebackException extends RuntimeException {
        public UnattributableChargebackException(String message) {
            super(message);
        }
    }

    /**
     * Resolves the acquirer that issued a reference.
     *
     * @return the acquirer, or {@link Acquirer#UNKNOWN} if no prefix matches
     */
    public Acquirer acquirerOf(String acquirerReference) {
        if (acquirerReference != null) {
            for (Map.Entry<String, Acquirer> prefix : PREFIXES.entrySet()) {
                if (acquirerReference.startsWith(prefix.getKey())) {
                    log.debug("matched chargeback to acquirer acquirerReference={} acquirer={}",
                            acquirerReference, prefix.getValue());
                    return prefix.getValue();
                }
            }
        }

        log.warn("chargeback reference from an acquirer we cannot attribute acquirerReference={}",
                acquirerReference);

        return Acquirer.UNKNOWN;
    }

    public enum Acquirer {
        WORLDPAY,
        ADYEN,

        /** No prefix matched. The chargeback cannot be attributed to a redemption. */
        UNKNOWN
    }
}
