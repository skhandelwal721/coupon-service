package com.northwind.coupon.chargeback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Matches an inbound chargeback to the redemption it should reverse.
 *
 * <p>Chargebacks arrive from the acquirer, not from billing-service, and they carry only the
 * acquirer's own reference. To find the redemption we have to know <em>which</em> acquirer
 * issued that reference, and the only thing that tells us is the prefix — billing-service
 * publishes {@code acquirerReference} as prefixed by acquirer
 * ({@code docs/api/openapi.yaml}, {@code acquirerReference.pattern}).
 *
 * <p><strong>Consequence of an unrecognised prefix.</strong> We cannot attribute the
 * chargeback, so the coupon liability is never reversed: the discount stays booked against a
 * charge that has since been clawed back. Nothing errors on the customer path, nothing
 * appears in the redemption error rate — the money is simply wrong, and it stays wrong until
 * someone reconciles the promotion ledger by hand. That is why an unknown prefix is a
 * hard failure here rather than a skipped record.
 */
@Component
public class ChargebackMatcher {

    private static final Logger log = LoggerFactory.getLogger(ChargebackMatcher.class);

    /** Worldpay settles Visa and Mastercard for billing-service, and prefixes its references. */
    static final String WORLDPAY_REFERENCE_PREFIX = "wp_";

    /** Thrown when a reference belongs to an acquirer we cannot attribute. */
    public static class UnattributableChargebackException extends RuntimeException {
        public UnattributableChargebackException(String message) {
            super(message);
        }
    }

    public Acquirer acquirerOf(String acquirerReference) {
        if (acquirerReference != null && acquirerReference.startsWith(WORLDPAY_REFERENCE_PREFIX)) {
            return Acquirer.WORLDPAY;
        }

        log.error("chargeback reference from an acquirer we cannot attribute acquirerReference={}",
                acquirerReference);

        throw new UnattributableChargebackException(
                "cannot attribute acquirer reference " + acquirerReference
                        + " — coupon liability for this charge cannot be reversed");
    }

    public enum Acquirer {
        WORLDPAY
    }
}
