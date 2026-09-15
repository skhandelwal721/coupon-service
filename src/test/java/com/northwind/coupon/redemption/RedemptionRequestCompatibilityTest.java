package com.northwind.coupon.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compatibility test against the request shape our pinned consumers actually send.
 *
 * <p><strong>This is the tripwire COUPON-491 did not have.</strong> That change added
 * {@code @NotBlank} to {@code deviceId} and {@code customerIp}. `order-service` is pinned to
 * contract 2.4.0 (`archetype-descriptor.yaml`, and `coupon.contract.version` in its `pom.xml`)
 * and sends neither field, so **every discounted checkout returned 400** — 19,760 orders a day
 * on the storefront path. Nothing in this repository noticed, because nothing here had ever
 * validated a 2.4.0-shaped request.
 *
 * <p>ECS-6.6 requires a change to a published contract to be tested against the actual
 * consumer's shape rather than a mock of it. These are those tests.
 */
class RedemptionRequestCompatibilityTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    private final Validator validator = FACTORY.getValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Exactly what a consumer pinned to 2.4.0 puts on the wire. Nothing more. */
    private static final String CONTRACT_2_4_0_REQUEST = """
            {
              "couponCode": "NW-VISA-10",
              "invoiceId": "inv-1001",
              "cardNumber": "4111111111111111",
              "currency": "GBP"
            }
            """;

    /** And what a 3.x storefront client sends. */
    private static final String CURRENT_REQUEST = """
            {
              "couponCode": "NW-VISA-10",
              "invoiceId": "inv-1001",
              "cardNumber": "4111111111111111",
              "currency": "GBP",
              "billingPostcode": "GB-EC2A4BX",
              "deviceId": "dev_7c2b91de",
              "customerIp": "203.0.113.7",
              "customerEmail": "shopper@example.com"
            }
            """;

    /** The load-bearing assertion: a 2.4.0 request must pass validation. */
    @Test
    void aRequestFromAConsumerPinnedTo240IsValid() throws Exception {
        RedemptionRequest request =
                mapper.readValue(CONTRACT_2_4_0_REQUEST, RedemptionRequest.class);

        var violations = validator.validate(request);

        assertTrue(violations.isEmpty(),
                "order-service is pinned to 2.4.0 and sends no device or origin — rejecting"
                        + " this shape returns 400 on every discounted checkout. Violations: "
                        + describe(violations));
    }

    @Test
    void aRequestFromAConsumerPinnedTo240DeserializesWithTheNewFieldsAbsent() throws Exception {
        RedemptionRequest request =
                mapper.readValue(CONTRACT_2_4_0_REQUEST, RedemptionRequest.class);

        assertEquals("NW-VISA-10", request.couponCode());
        assertEquals("inv-1001", request.invoiceId());
        assertEquals("GBP", request.currency());
        assertNull(request.deviceId());
        assertNull(request.customerIp());
        assertNull(request.billingPostcode());
        assertNull(request.customerEmail());
    }

    @Test
    void theCurrentStorefrontShapeIsAlsoValid() throws Exception {
        RedemptionRequest request = mapper.readValue(CURRENT_REQUEST, RedemptionRequest.class);

        assertTrue(validator.validate(request).isEmpty(), describe(validator.validate(request)));
        assertEquals("dev_7c2b91de", request.deviceId());
        assertEquals("203.0.113.7", request.customerIp());
    }

    /** The fields that were always required stay required. Optional is not a free-for-all. */
    @Test
    void theFieldsThatWereAlwaysRequiredAreStillRequired() throws Exception {
        String missingCoupon = """
                {
                  "invoiceId": "inv-1001",
                  "cardNumber": "4111111111111111",
                  "currency": "GBP"
                }
                """;

        var violations =
                validator.validate(mapper.readValue(missingCoupon, RedemptionRequest.class));

        assertEquals(1, violations.size(), describe(violations));
        assertTrue(describe(violations).contains("couponCode"));
    }

    @Test
    void anInvalidCurrencyIsStillRejected() throws Exception {
        String badCurrency = CONTRACT_2_4_0_REQUEST.replace("\"GBP\"", "\"USD\"");

        assertEquals(1,
                validator.validate(mapper.readValue(badCurrency, RedemptionRequest.class)).size());
    }

    /** An email is optional, but a malformed one is still a client error. */
    @Test
    void aMalformedEmailIsStillRejected() throws Exception {
        String badEmail = CURRENT_REQUEST.replace("shopper@example.com", "not-an-email");

        assertEquals(1,
                validator.validate(mapper.readValue(badEmail, RedemptionRequest.class)).size());
    }

    private static String describe(Set<? extends jakarta.validation.ConstraintViolation<?>> v) {
        return v.stream()
                .map(x -> x.getPropertyPath() + " " + x.getMessage())
                .collect(Collectors.joining("; "));
    }
}
