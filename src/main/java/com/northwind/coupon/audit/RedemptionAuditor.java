package com.northwind.coupon.audit;

import com.northwind.coupon.billing.BillingChargeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Checks that a charge we are about to discount is one we can account for.
 *
 * <p><strong>The identity is {@code subtotal + tax == total}.</strong> billing-service
 * publishes it as a response invariant ({@code docs/api/openapi.yaml},
 * {@code x-invariant} on {@code ChargeResponse}), and it is the only arithmetic tie between a
 * charge and the discount we booked against it.
 *
 * <p>A total that carries something the subtotal and tax do not account for means one of two
 * things: the charge was mispriced, or the contract changed and there is now money in the
 * total we cannot see. Both are escalations, not rounding differences — so we hold the
 * redemption instead of applying a discount against a number we cannot reconstruct. A
 * discount applied to a charge we cannot explain is a write-off waiting to be found by
 * finance.
 */
@Component
public class RedemptionAuditor {

    private static final Logger log = LoggerFactory.getLogger(RedemptionAuditor.class);

    /** Thrown when a charge does not satisfy the published invariant. The redemption is held. */
    public static class UnaccountableChargeException extends RuntimeException {
        public UnaccountableChargeException(String message) {
            super(message);
        }
    }

    public void requireAccountable(BillingChargeView charge) {
        BigDecimal expected = charge.subtotal().add(charge.surcharge()).add(charge.tax());

        if (expected.compareTo(charge.total()) != 0) {
            BigDecimal unexplained = charge.total().subtract(expected);
            log.error("charge does not balance chargeId={} subtotal={} surcharge={} tax={} total={} unexplained={}",
                    charge.chargeId(), charge.subtotal(), charge.surcharge(), charge.tax(),
                    charge.total(), unexplained);

            throw new UnaccountableChargeException(
                    "charge " + charge.chargeId() + " does not balance: subtotal " + charge.subtotal()
                            + " + surcharge " + charge.surcharge()
                            + " + tax " + charge.tax() + " != total " + charge.total()
                            + " (" + unexplained + " unaccounted for)");
        }
    }
}
