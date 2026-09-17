package com.beaconstone.coupon.billing;

import com.beaconstone.coupon.sepa.SepaAddressNormaliser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BillingClientTest {

    private final BillingClient client = new BillingClient(
            "http://billing-service.prod.internal",
            "/v1/invoices/{invoiceId}/charge",
            new SepaAddressNormaliser(),
            new CardMask());

    @Test
    void chargesTheInvoiceAndReadsTheChargeBack() {
        BillingChargeView charge = client.charge(
                "inv-1001", "4111111111111111", "GBP", "GB-EC2A4BX");

        assertEquals("chg_9f3b7c21", charge.chargeId());
        assertEquals("inv-1001", charge.invoiceId());
        assertEquals("GBP", charge.currency());
        assertNotNull(charge.acquirerReference());
    }

    /**
     * The invoice is charged in full. Nothing is deducted at the charge.
     *
     * <p>COUPON-530 deducted the promotion here. That reduced the payable amount twice, because
     * `order-service` subtracts the same discount again from what it shows the shopper — and it
     * has no field on its receipt that could tell it otherwise.
     */
    @Test
    void chargesTheFullInvoiceAmount() {
        BillingChargeView charge = client.charge(
                "inv-1001", "4111111111111111", "GBP", "GB-EC2A4BX");

        assertEquals(new BigDecimal("249.00"), charge.subtotal(),
                "the charge carries the full invoice amount — no deduction is applied here");
        assertEquals(0, charge.subtotal().add(charge.tax()).compareTo(charge.total()),
                "subtotal + tax == total, as billing-service publishes it");
    }

    /**
     * <strong>The tripwire.</strong> No monetary amount may leave this service towards the
     * payment processor.
     *
     * <p>An amount sent to a payment processor is an instruction to move money, and it needs a
     * bound, a unit asserted at the boundary, and a per-transaction record before it can be
     * sent safely. None of that exists here yet, and the prerequisites in
     * {@code docs/api/promotional-adjustment-prerequisites.md} are not met.
     *
     * <p>If someone re-adds an amount parameter to this client, this fails the build rather
     * than production.
     */
    @Test
    void noMonetaryAmountIsSentToThePaymentProcessor() {
        for (Method method : BillingClient.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(BigDecimal.class.equals(parameter) || Number.class.isAssignableFrom(parameter),
                        method.getName() + " takes a monetary parameter (" + parameter.getSimpleName()
                                + "). An amount sent to a payment processor needs a bound, an "
                                + "asserted unit and an audit record first — see "
                                + "docs/api/promotional-adjustment-prerequisites.md");
            }
        }
    }

    @Test
    void thePostcodeIsForwardedForVatResolution() {
        BillingChargeView charge = client.charge(
                "inv-1001", "4111111111111111", "EUR", "DE-10115");

        assertEquals("EUR", charge.currency());
    }

    @Test
    void handlesAnAbsentPostcode() {
        BillingChargeView charge = client.charge(
                "inv-1001", "4111111111111111", "GBP", null);

        assertEquals("chg_9f3b7c21", charge.chargeId());
    }
}
