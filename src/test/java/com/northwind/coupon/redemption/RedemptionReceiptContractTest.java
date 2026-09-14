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
     * discount serializes as {@code 24.90}, not {@code 2490}. Contract 2.4.0 published this
     * meaning and every consumer pinned to it depends on it.
     */
    @Test
    void discountIsPublishedInMajorUnits() throws Exception {
        JsonNode receipt = mapper.valueToTree(receipt("24.90"));

        assertEquals(new BigDecimal("24.90"), receipt.get("discount").decimalValue(),
                "discount must stay an absolute amount in major units — a consumer pinned to"
                        + " 2.4.0 reads this field and cannot tell if the basis changed");
    }

    /** The SEPA representation lives in its own field, under its own name. */
    @Test
    void minorUnitsArePublishedInTheirOwnField() throws Exception {
        JsonNode receipt = mapper.valueToTree(receipt("24.90"));

        assertEquals(new BigDecimal("2490"), receipt.get("discountMinorUnits").decimalValue());
    }

    /** Both fields describe one amount, so they can never disagree. */
    @Test
    void theTwoRepresentationsAgree() {
        RedemptionReceipt receipt = receipt("24.90");

        assertEquals(RedemptionReceipt.toMinorUnits(receipt.discount()),
                receipt.discountMinorUnits());
    }

    @Test
    void agreementHoldsForEveryCatalogueAmount() {
        for (String amount : new String[] {"10.00", "15.00", "24.90", "25.00", "249.00"}) {
            RedemptionReceipt receipt = receipt(amount);
            assertEquals(RedemptionReceipt.toMinorUnits(receipt.discount()),
                    receipt.discountMinorUnits(),
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
        assertEquals(new BigDecimal("24.90"), receipt.get("discount").decimalValue());
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

        assertEquals(new BigDecimal("10.00"), legacy.discount());
        assertEquals(new BigDecimal("1000"), legacy.discountMinorUnits());
        assertEquals("GBP", legacy.settlementCurrency());
    }

    /** A fraction of a minor unit cannot be instructed, so conversion truncates. */
    @Test
    void minorUnitConversionTruncates() {
        assertEquals(new BigDecimal("1249"),
                RedemptionReceipt.toMinorUnits(new BigDecimal("12.499")));
        assertEquals(new BigDecimal("1250"),
                RedemptionReceipt.toMinorUnits(new BigDecimal("12.501")));
    }

    private static RedemptionReceipt receipt(String discount) {
        BigDecimal amount = new BigDecimal(discount);
        return new RedemptionReceipt("rdm_4f8a21c7", "NW-SEPA-25", "chg_9f3b7c21", "VISA",
                amount, RedemptionReceipt.toMinorUnits(amount), "EUR", "REDEEMED");
    }
}
