package com.northwind.coupon.billing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
     * Guard rail for a contract change upstream.
     *
     * <p>billing-service declares {@code additionalProperties: false} on {@code ChargeResponse},
     * so a new field is a breaking change for us, not an additive one. We assert the failure
     * deliberately: if this test ever goes green with an extra field present, it means someone
     * relaxed our deserializer and we have lost the signal that their contract moved.
     */
    @Test
    void rejectsAResponseCarryingAFieldTheContractDoesNotDeclare() {
        String withUnknownField = """
                {
                  "chargeId": "chg_9f3b7c21",
                  "invoiceId": "inv-1001",
                  "subtotal": "249.00",
                  "surcharge": "3.74",
                  "tax": "49.80",
                  "total": "298.80",
                  "currency": "GBP",
                  "cardType": "VISA",
                  "acquirerReference": "wp_4f8a21c7",
                  "status": "CHARGED"
                }
                """;

        assertThrows(Exception.class,
                () -> mapper.readValue(withUnknownField, BillingChargeView.class),
                "an undeclared response field must fail loudly, not be dropped silently");
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
