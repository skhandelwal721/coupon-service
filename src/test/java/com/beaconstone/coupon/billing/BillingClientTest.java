package com.beaconstone.coupon.billing;

import com.beaconstone.coupon.sepa.SepaAddressNormaliser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                "inv-1001", "4111111111111111", "GBP", "GB-EC2A4BX", new BigDecimal("2490"));

        assertEquals("chg_9f3b7c21", charge.chargeId());
        assertEquals("inv-1001", charge.invoiceId());
        assertEquals("GBP", charge.currency());
        assertNotNull(charge.acquirerReference());
    }

    @Test
    void sendsThePromotionalAdjustmentWithTheCharge() {
        BillingChargeView charge = client.charge(
                "inv-1001", "4111111111111111", "EUR", "DE-10115", new BigDecimal("1000"));

        assertEquals("EUR", charge.currency());
    }

    @Test
    void handlesAnUndiscountedCharge() {
        BillingChargeView charge = client.charge(
                "inv-1001", "4111111111111111", "GBP", "GB-EC2A4BX", null);

        assertEquals("chg_9f3b7c21", charge.chargeId());
    }
}
