package com.northwind.coupon.redemption;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the redemption receipt we publish.
 *
 * <p><strong>This is the tripwire COUPON-490 did not have.</strong> That change reinterpreted
 * {@code discount} from major units to minor units. Nothing caught it — the field kept its name
 * and its {@code BigDecimal} type, so the compiler, JSON schema validation, every consumer's
 * deserializer and this repository's own suite all stayed green while every consumer read the
 * figure a hundred times too small.
 *
 * <p>The tests below pin the <em>basis</em> of each monetary field on the wire, not just its
 * presence. A future change to what {@code discount} means fails here.
 */
class RedemptionReceiptContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * The load-bearing assertion.
     *
     * <p>{@code discount} is the absolute amount off in <strong>major units</strong>. A 24.90
     * discount is published as 24.90, not 2490 — a hundred-fold difference in magnitude, which
     * is what {@link #assertAmountEquals} checks. Contract 2.4.0 published this meaning and
     * every consumer pinned to it depends on it.
     */
    @Test
    void discountIsPublishedInMajorUnits() throws Exception {
        JsonNode receipt = mapper.valueToTree(receipt("24.90"));

        assertAmountEquals("24.90", receipt.get("discount").decimalValue(),
                "discount must stay an absolute amount in major units — a consumer pinned to"
                        + " 2.4.0 reads this field and cannot tell if the basis changed");
    }

    /** The SEPA representation lives in its own field, under its own name. */
    @Test
    void minorUnitsArePublishedInTheirOwnField() throws Exception {
        JsonNode receipt = mapper.valueToTree(receipt("24.90"));

        assertAmountEquals("2490", receipt.get("discountMinorUnits").decimalValue(),
                "the SEPA representation belongs in its own field");
    }

    /** Both fields describe one amount, so they can never disagree. */
    @Test
    void theTwoRepresentationsAgree() {
        RedemptionReceipt receipt = receipt("24.90");

        assertEquals(0,
                RedemptionReceipt.toMinorUnits(receipt.discount())
                        .compareTo(receipt.discountMinorUnits()));
    }

    @Test
    void agreementHoldsForEveryCatalogueAmount() {
        for (String amount : new String[] {"10.00", "15.00", "24.90", "25.00", "249.00"}) {
            RedemptionReceipt receipt = receipt(amount);
            assertEquals(0,
                    RedemptionReceipt.toMinorUnits(receipt.discount())
                            .compareTo(receipt.discountMinorUnits()),
                    "representations disagree for " + amount);
        }
    }

    /**
     * What a consumer pinned to 2.4.0 sees. It reads exactly the four fields it knows about,
     * on exactly the basis it was written against, and ignores the two it does not.
     */
    @Test
    void aConsumerPinnedToTheOldContractStillReadsTheRightNumber() throws Exception {
        JsonNode receipt = mapper.valueToTree(receipt("24.90"));

        assertEquals("rdm_4f8a21c7", receipt.get("redemptionId").asText());
        assertEquals("NW-SEPA-25", receipt.get("couponCode").asText());
        assertEquals("VISA", receipt.get("fundingNetwork").asText());
        assertEquals("REDEEMED", receipt.get("status").asText());
        assertAmountEquals("24.90", receipt.get("discount").decimalValue(),
                "the field a 2.4.0 consumer reads");
    }

    /** The new fields are additive: present, and not replacing anything. */
    @Test
    void theNewFieldsAreAdditive() throws Exception {
        JsonNode receipt = mapper.valueToTree(receipt("24.90"));

        assertTrue(receipt.has("discount"));
        assertTrue(receipt.has("discountMinorUnits"));
        assertTrue(receipt.has("settlementCurrency"));
        assertEquals(8, receipt.size(), "no field removed, none renamed");
    }

    /** The back-compatible constructor derives minor units rather than guessing a basis. */
    @Test
    void theBackCompatibleConstructorDerivesMinorUnitsFromTheDocumentedBasis() {
        RedemptionReceipt legacy = new RedemptionReceipt("rdm_1", "NW-VISA-10", "chg_1",
                "VISA", new BigDecimal("10.00"), "REDEEMED");

        assertAmountEquals("10.00", legacy.discount(), "documented basis preserved");
        assertAmountEquals("1000", legacy.discountMinorUnits(), "derived, not guessed");
        assertEquals("GBP", legacy.settlementCurrency());
    }

    /** A fraction of a minor unit cannot be instructed, so conversion truncates. */
    @Test
    void minorUnitConversionTruncates() {
        assertAmountEquals("1249", RedemptionReceipt.toMinorUnits(new BigDecimal("12.499")),
                "a fraction of a minor unit cannot be instructed");
        assertAmountEquals("1250", RedemptionReceipt.toMinorUnits(new BigDecimal("12.501")),
                "a fraction of a minor unit cannot be instructed");
    }

    /**
     * Compares magnitude, not scale.
     *
     * <p>The defect this whole class exists for is a hundred-fold magnitude error, and that is
     * what has to be asserted. Trailing-zero scale is a JSON representation detail — Jackson
     * normalises {@code 24.90} to {@code 24.9} through a tree node — and pinning it would make
     * these tests fail for a cosmetic reason and stop being trusted.
     */
    private static void assertAmountEquals(String expected, BigDecimal actual, String why) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                why + " — expected " + expected + " but was " + actual);
    }

    private static RedemptionReceipt receipt(String discount) {
        BigDecimal amount = new BigDecimal(discount);
        return new RedemptionReceipt("rdm_4f8a21c7", "NW-SEPA-25", "chg_9f3b7c21", "VISA",
                amount, RedemptionReceipt.toMinorUnits(amount), "EUR", "REDEEMED");
    }
}
