package com.northwind.coupon.ledger;

import com.northwind.coupon.billing.CardNetwork;
import com.northwind.coupon.redemption.RedemptionReceipt;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionLedgerTest {

    @Test
    void booksADiscountAgainstTheFundingNetwork() {
        PromotionLedger ledger = new PromotionLedger();
        ledger.book(receipt("VISA", "10.00"));
        ledger.book(receipt("VISA", "25.00"));
        ledger.book(receipt("MASTERCARD", "15.00"));

        assertEquals(new BigDecimal("35.00"), ledger.liabilityFor(CardNetwork.VISA));
        assertEquals(new BigDecimal("15.00"), ledger.liabilityFor(CardNetwork.MASTERCARD));
    }

    /**
     * Guard rail for adding a card network: every network we can attribute a promotion to must
     * have a liability account, otherwise we cannot invoice that network for its share.
     *
     * <p>Note what this does <em>not</em> guard. It checks that an account exists for every
     * enum value. It cannot check that the figure booked into that account still means what it
     * meant when the account was opened — {@code discount} is a {@link BigDecimal} either way.
     * A change in the meaning of that field leaves this test green and the ledger wrong.
     */
    /**
     * The basis regression for COUPON-490.
     *
     * <p>These accounts hold major units. A receipt carrying 24.90 must book 24.90 — not 2490.
     * COUPON-490 booked {@code discountMinorUnits} here and overstated network liability by a
     * factor of one hundred, which is the figure we invoice the card networks from.
     */
    @Test
    void booksMajorUnitsNotMinorUnits() {
        PromotionLedger ledger = new PromotionLedger();
        ledger.book(receipt("VISA", "24.90"));

        assertEquals(new BigDecimal("24.90"), ledger.liabilityFor(CardNetwork.VISA),
                "the ledger books major units — booking minor units here overstates the"
                        + " liability we invoice the networks from by 100x");
    }

    /** A euro promotion books on the same basis as a sterling one. */
    @Test
    void booksTheSameBasisRegardlessOfSettlementCurrency() {
        PromotionLedger ledger = new PromotionLedger();
        ledger.book(new RedemptionReceipt("rdm_eur", "NW-SEPA-25", "chg_1", "VISA",
                new BigDecimal("25.00"), new BigDecimal("2500"), "EUR", "REDEEMED"));

        assertEquals(new BigDecimal("25.00"), ledger.liabilityFor(CardNetwork.VISA));
    }

    @Test
    void everyFundingNetworkHasALiabilityAccount() {
        PromotionLedger ledger = new PromotionLedger();
        for (CardNetwork network : CardNetwork.values()) {
            assertTrue(ledger.hasAccount(network),
                    "no promotion liability account for " + network
                            + " — this network's share cannot be invoiced");
        }
    }

    private static RedemptionReceipt receipt(String fundingNetwork, String discount) {
        return new RedemptionReceipt("rdm_1", "NW-VISA-10", "chg_1",
                fundingNetwork, new BigDecimal(discount), "REDEEMED");
    }
}
