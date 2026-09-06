package com.northwind.coupon.billing;

/**
 * The card networks billing-service can charge.
 *
 * <p>This mirrors {@code com.northwind.billing.card.CardNetwork} and is kept in step with
 * billing-service's published contract — {@code cardType} in the charge response carries one
 * of these values. See {@code docs/dependencies.md}.
 *
 * <p><strong>This enum is the closed set we price promotions against.</strong> Network
 * promotions are funded by a specific network's interchange rebate, so a redemption we cannot
 * attribute to a known network is a discount nobody has agreed to pay for. That is why
 * {@link #fromChargeResponse} throws rather than defaulting.
 */
public enum CardNetwork {

    VISA,
    MASTERCARD;

    /**
     * Resolves the network from the {@code cardType} field of a billing-service charge
     * response.
     *
     * <p>billing-service 4.12.0 moved the network to {@code cardNetwork}; {@code cardType} now
     * carries the funding type. Callers pass {@code cardNetwork}.
     *
     * @throws IllegalArgumentException if {@code cardType} is not a network we know
     */
    public static CardNetwork fromChargeResponse(String cardType) {
        return CardNetwork.valueOf(cardType);
    }
}
