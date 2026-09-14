package com.northwind.coupon.ledger;

import com.northwind.coupon.billing.CardNetwork;
import com.northwind.coupon.redemption.RedemptionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * The promotion liability ledger.
 *
 * <p>Every redemption books a liability against the network that funds the promotion. At the
 * end of the month we invoice each network for the discounts their interchange rebate paid
 * for, and this ledger is the number we invoice from.
 *
 * <p><strong>{@code discount} is booked as an absolute currency amount in major units.</strong>
 * That is what {@code RedemptionReceipt} carries and what {@code docs/api/redemption.md}
 * documents. The ledger does no conversion — it adds the figure to the network's account as-is,
 * so <em>every</em> figure reaching {@link #book} must be on that basis.
 *
 * <p><strong>Never book {@code discountMinorUnits} here.</strong> These accounts hold major
 * units from the day they were opened and there is no cutover; mixing bases in one accumulator
 * produces a total that is arithmetically valid and financially meaningless. COUPON-490 booked
 * minor units into these accounts and overstated network liability by a factor of one hundred —
 * the figure we invoice the card networks from. If a minor-unit total is ever needed, derive it
 * on read rather than changing what these accounts hold.
 *
 * <p>Worth being explicit about the failure mode this cannot catch: the field is a
 * {@link BigDecimal}, so a figure that stopped being an absolute amount would still add
 * cleanly. The account would simply hold the wrong number, we would invoice the networks for
 * the wrong number, and every type in the chain would be correct. Nothing here or in the
 * tests below can distinguish 10.00-the-amount from 10.00-the-percentage.
 */
@Component
public class PromotionLedger {

    private static final Logger log = LoggerFactory.getLogger(PromotionLedger.class);

    private final Map<CardNetwork, BigDecimal> liability = new EnumMap<>(CardNetwork.class);

    public PromotionLedger() {
        for (CardNetwork network : CardNetwork.values()) {
            liability.put(network, BigDecimal.ZERO.setScale(2));
        }
    }

    public void book(RedemptionReceipt receipt) {
        CardNetwork network = CardNetwork.fromChargeResponse(receipt.fundingNetwork());

        BigDecimal updated = liability.get(network).add(receipt.discount());
        liability.put(network, updated);

        log.info("booked promotion liability redemptionId={} network={} discount={} accountTotal={}",
                receipt.redemptionId(), network, receipt.discount(), updated);
    }

    public BigDecimal liabilityFor(CardNetwork network) {
        return liability.get(network);
    }

    public boolean hasAccount(CardNetwork network) {
        return liability.containsKey(network);
    }
}
