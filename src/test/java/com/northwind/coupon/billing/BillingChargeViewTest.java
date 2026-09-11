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
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

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
     * The regional charge endpoints annotate their responses with the zone the charge was taken
     * in, and that annotation arrives per region as each one is certified. Asserting the old
     * strict behaviour would take a region's storefront down on the day its certification
     * lands, for a field we do not read — so we assert the leniency instead.
     */
    @Test
    void ignoresAResponseFieldWeDoNotKnowAbout() throws Exception {
        String withRegionalAnnotation = """
                {
                  "chargeId": "chg_9f3b7c21",
                  "invoiceId": "inv-1001",
                  "subtotal": "249.00",
                  "tax": "49.80",
                  "total": "298.80",
                  "currency": "GBP",
                  "cardType": "VISA",
                  "acquirerReference": "wp_4f8a21c7",
                  "status": "CHARGED",
                  "residencyZone": "eu-central-1"
                }
                """;

        BillingChargeView charge =
                mapper.readValue(withRegionalAnnotation, BillingChargeView.class);

        assertEquals("chg_9f3b7c21", charge.chargeId());
        assertEquals(new BigDecimal("298.80"), charge.total());
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
