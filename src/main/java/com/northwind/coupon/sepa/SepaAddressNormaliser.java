package com.northwind.coupon.sepa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Normalises a customer postcode into SEPA structured-address form.
 *
 * <p>SEPA instructions carry the debtor's structured address (ISO 20022
 * {@code PstlAdr/PstCd}). The scheme's implementation guidelines restrict that element to
 * alphanumerics: separators, spaces and punctuation are not permitted, and an instruction
 * carrying them is rejected at the clearing house rather than at our edge.
 *
 * <p>The storefront collects postcodes in display form — {@code "DE-10115"}, {@code "EC2A 4BX"} —
 * so we normalise before the figure leaves us: strip everything that is not alphanumeric and
 * upper-case the rest.
 *
 * <h2>Where this may be used, and where it may not</h2>
 *
 * <p><strong>Settlement instructions only.</strong> The scheme's address restrictions apply to
 * the instruction, and nowhere else.
 *
 * <p><strong>It must not be applied to the charge request.</strong> billing-service reads
 * {@code billingPostcode} as its VAT place-of-supply input and resolves the member state by
 * matching the country prefix — {@code "DE-"}, {@code "FR-"} — separator included
 * ({@code PlaceOfSupply.forPostcode}). A normalised {@code "DE10115"} does not match, so it
 * falls back to the merchant's home jurisdiction and the supply is taxed at the UK rate and
 * declared in the wrong member state. Nothing throws; the charge succeeds at the wrong rate.
 *
 * <p>COUPON-490 applied this on the charge path. COUPON-495 removed it. See
 * {@link com.northwind.coupon.billing.BillingClient#postcodeForCharge}.
 *
 * <p>It is a presentation change only, in the sense that no information is discarded:
 * {@code "DE-10115"} and {@code "DE10115"} identify the same postcode. But "no information
 * discarded" is not the same as "safe to substitute" — a consumer that parses the separator
 * reads a different answer from the same information.
 */
@Component
public class SepaAddressNormaliser {

    private static final Logger log = LoggerFactory.getLogger(SepaAddressNormaliser.class);

    /** Everything SEPA's structured-address element does not accept. */
    private static final String NON_ALPHANUMERIC = "[^A-Za-z0-9]";

    /**
     * The postcode as SEPA will accept it.
     *
     * @param postcode as collected by the storefront, may be {@code null}
     * @return the normalised postcode, or {@code null} if there was nothing to normalise
     */
    public String normalise(String postcode) {
        if (postcode == null || postcode.isBlank()) {
            return null;
        }

        String normalised = postcode.replaceAll(NON_ALPHANUMERIC, "").toUpperCase();

        if (!normalised.equals(postcode)) {
            log.debug("normalised postcode for SEPA structured address");
        }

        return normalised.isEmpty() ? null : normalised;
    }
}
