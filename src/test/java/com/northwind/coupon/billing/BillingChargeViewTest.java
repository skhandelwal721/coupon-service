package com.northwind.coupon.billing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Contract test for billing-service's charge response.
 *
 * <p>This is the shape we generate against, taken verbatim from billing-service
 * {@code docs/api/charge.md}. It exists so that a change to their published contract shows up
 * here as a red build rather than in production as a discount we cannot reconcile.
 */
class BillingChargeViewTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** The response shape billing-service publishes today, field for field. */
    private static final String PUBLISHED_RESPONSE = """
            {
              "chargeId": "chg_9f3b7c21",
              "invoiceId": "inv-1001",
              "subtotal": "249.00",
              "tax": "49.80",
              "total": "298.80",
              "currency": "GBP",
              "cardType": "VISA",
              "acquirerReference": "wp_4f8a21c7",
              "status": "CHARGED"
            }
            """;

    @Test
    void deserializesThePublishedChargeResponse() throws Exception {
        BillingChargeView charge = mapper.readValue(PUBLISHED_RESPONSE, BillingChargeView.class);

        assertEquals("chg_9f3b7c21", charge.chargeId());
        assertEquals("VISA", charge.cardType());
        assertEquals("wp_4f8a21c7", charge.acquirerReference());
        assertEquals(new BigDecimal("298.80"), charge.total());
    }

    /**
     * billing-service relaxed {@code additionalProperties} to {@code true} in 4.12.0 and
     * documents new response fields as additive. Asserting the old strict behaviour would fail
     * the build — and take checkout down — every time they ship a field, which is an outage we
     * would be causing ourselves. So we assert the leniency instead.
     */
    @Test
    void ignoresAResponseFieldWeDoNotKnowAbout() throws Exception {
        String withUnknownField = """
                {
                  "chargeId": "chg_9f3b7c21",
                  "invoiceId": "inv-1001",
                  "subtotal": "249.00",
                  "surcharge": "3.74",
                  "tax": "49.80",
                  "total": "302.54",
                  "currency": "GBP",
                  "cardType": "CHARGE_CARD",
                  "cardNetwork": "AMEX",
                  "acquirerReference": "amex_4f8a21c7",
                  "status": "CHARGED",
                  "settlementBatchId": "btc_20260901_02"
                }
                """;

        BillingChargeView charge = mapper.readValue(withUnknownField, BillingChargeView.class);

        assertEquals("chg_9f3b7c21", charge.chargeId());
        assertEquals("AMEX", charge.cardNetwork());
        assertEquals(new BigDecimal("3.74"), charge.surcharge());
    }

    /**
     * {@code cardType} is the card network. This pins the assumption every promotion decision
     * in this service rests on.
     */
    @Test
    void cardTypeCarriesTheCardNetwork() throws Exception {
        BillingChargeView charge = mapper.readValue(PUBLISHED_RESPONSE, BillingChargeView.class);

        assertEquals(CardNetwork.VISA, CardNetwork.fromChargeResponse(charge.cardType()));
    }
}
