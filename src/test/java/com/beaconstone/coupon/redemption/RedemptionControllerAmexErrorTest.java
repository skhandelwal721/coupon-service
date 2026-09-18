package com.beaconstone.coupon.redemption;

import com.beaconstone.coupon.analytics.RedemptionAnalyticsClient;
import com.beaconstone.coupon.fraud.VelocityGuard;
import com.beaconstone.coupon.payments.AmexEuropeEligibility;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAY-8100 — the AMEX eligibility gate throws {@link AmexEuropeEligibility.AmexNotOfferedException}
 * when AMEX is not offered. Without a handler that would surface to the customer as a generic
 * {@code 500}; this test proves the controller maps it to {@code 404} instead, so a deliberate,
 * pre-charge refusal is reported as "not on offer" rather than a server error.
 */
class RedemptionControllerAmexErrorTest {

    @Test
    void amexNotOfferedIsMappedTo404NotAGeneric500() throws Exception {
        RedemptionService service = Mockito.mock(RedemptionService.class);
        VelocityGuard velocityGuard = Mockito.mock(VelocityGuard.class);
        RedemptionAnalyticsClient analytics = Mockito.mock(RedemptionAnalyticsClient.class);

        // The gate has refused this order before any charge; the service surfaces that refusal.
        Mockito.when(service.redeem(Mockito.any()))
                .thenThrow(new AmexEuropeEligibility.AmexNotOfferedException("US"));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new RedemptionController(service, velocityGuard, analytics))
                .build();

        String body = "{"
                + "\"couponCode\":\"BS-EU-20\","
                + "\"invoiceId\":\"inv-1001\","
                + "\"cardNumber\":\"378282246310005\","
                + "\"currency\":\"EUR\","
                + "\"deviceId\":\"dev-1\","
                + "\"customerIp\":\"203.0.113.7\","
                + "\"billingCountry\":\"US\""
                + "}";

        mockMvc.perform(post("/v1/redemptions")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
