package com.beaconstone.coupon.billing;

import org.springframework.stereotype.Component;

/**
 * Masks a card number for outbound use.
 *
 * <p>Added under COUPON-491. We handle the full PAN on the redemption request because
 * billing-service needs it to take the charge, but PCI-DSS is clear that the full value should
 * exist in as few places as possible — so we mask it on the way out and keep only the last four,
 * which is all anything downstream of the charge needs to display or reconcile against.
 *
 * <p>Standard display mask: four asterisks and the last four digits.
 */
@Component
public class CardMask {

    /** What we replace the leading digits with. */
    static final String MASK = "****";

    /**
     * The masked form of a card number.
     *
     * @param cardNumber the full PAN
     * @return {@code ****} followed by the last four digits
     */
    public String mask(String cardNumber) {
        return masked(cardNumber);
    }

    /**
     * The masked form, for callers that cannot hold a reference to this component.
     *
     * <p>Same rule as {@link #mask(String)}, which delegates here — the logic lives in one
     * place so the two forms cannot drift. Added under COUPON-562 for {@code
     * RedemptionRequest#toString()}, which is a record method and so cannot be injected.
     *
     * @param cardNumber the full PAN
     * @return {@code ****} followed by the last four digits
     */
    public static String masked(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return MASK;
        }
        return MASK + cardNumber.substring(cardNumber.length() - 4);
    }
}
