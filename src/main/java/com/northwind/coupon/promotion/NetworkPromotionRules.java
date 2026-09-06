package com.northwind.coupon.promotion;

import com.northwind.coupon.billing.BillingChargeView;
import com.northwind.coupon.billing.CardNetwork;
import org.springframework.stereotype.Component;

/**
 * Decides whether a coupon may be applied to the charge that settled it.
 *
 * <p>Network promotions are funded by one network's interchange rebate. Applying a
 * Visa-funded promotion to a Mastercard charge is unfunded discount — real money out with no
 * rebate in — so eligibility is resolved from the network on the charge, every time.
 *
 * <p>The network comes from billing-service's {@code cardNetwork} field as of 4.12.0.
 */
@Component
public class NetworkPromotionRules {

    public boolean isEligible(Coupon coupon, BillingChargeView charge) {
        CardNetwork network = CardNetwork.fromChargeResponse(charge.cardNetwork());
        return coupon.fundedBy().contains(network);
    }

    /**
     * The funding network for a charge, for attribution reporting.
     *
     * @throws IllegalArgumentException if the charge's {@code cardType} is not a known network
     */
    public CardNetwork fundingNetwork(BillingChargeView charge) {
        return CardNetwork.fromChargeResponse(charge.cardNetwork());
    }
}
