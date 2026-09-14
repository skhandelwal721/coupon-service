package com.northwind.coupon.billing;

import com.northwind.coupon.sepa.SepaAddressNormaliser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the postcode we put on a charge request.
 *
 * <p>billing-service resolves the VAT member state by matching the country prefix on this exact
 * string — {@code PlaceOfSupply.forPostcode}, which matches {@code "DE-"}, {@code "FR-"} and so
 * on, separator included. That makes the string format part of our outbound contract with them,
 * so it is pinned here rather than left as an implementation detail.
 *
 * <p>COUPON-490 normalised it to SEPA structured-address form on this path. The prefix stopped
 * matching, billing fell back to the merchant's home jurisdiction, and every euro charge was
 * taxed at the UK rate and declared in the wrong member state — with no error raised anywhere.
 */
class BillingClientTest {

    private final BillingClient client = new BillingClient(
            "http://billing-service.prod.internal", "/v1/invoices/{invoiceId}/charge");

    private final SepaAddressNormaliser normaliser = new SepaAddressNormaliser();

    /** The assertion that would have caught COUPON-490. */
    @Test
    void sendsThePostcodeExactlyAsTheStorefrontCollectedIt() {
        assertEquals("DE-10115", client.postcodeForCharge("DE-10115"));
        assertEquals("FR-75001", client.postcodeForCharge("FR-75001"));
        assertEquals("EC2A 4BX", client.postcodeForCharge("EC2A 4BX"));
    }

    /**
     * The two forms are not interchangeable, and this states why in one place.
     *
     * <p>If someone routes the charge path through the normaliser again, this fails.
     */
    @Test
    void doesNotSendTheSepaNormalisedForm() {
        String collected = "DE-10115";

        assertNotEquals(normaliser.normalise(collected), client.postcodeForCharge(collected),
                "the SEPA form belongs on the settlement instruction, not on the charge — "
                        + "billing-service matches the country prefix including the separator");
    }

    /** The country prefix billing matches on has to survive intact. */
    @Test
    void preservesTheCountryPrefixWithItsSeparator() {
        assertEquals("DE-", client.postcodeForCharge("DE-10115").substring(0, 3));
        assertEquals("ES-", client.postcodeForCharge("ES-28001").substring(0, 3));
    }

    /** No postcode is still no postcode — we do not invent one. */
    @Test
    void sendsNothingWhenTheStorefrontCollectedNothing() {
        assertNull(client.postcodeForCharge(null));
    }

    @Test
    void stillReturnsTheChargeItReadBack() {
        BillingChargeView charge = client.charge("inv-1001", "4111111111111111", "EUR", "DE-10115");

        assertEquals("chg_9f3b7c21", charge.chargeId());
        assertEquals("inv-1001", charge.invoiceId());
        assertEquals("EUR", charge.currency());
    }
}
