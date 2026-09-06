package com.northwind.coupon.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Our view of a billing-service charge response.
 *
 * <p>Generated from billing-service {@code docs/api/openapi.yaml}, schema
 * {@code ChargeResponse}, pinned at the version in {@code billing.contract.version}.
 *
 * <p>Now lenient. billing-service relaxed {@code additionalProperties} to {@code true} in
 * 4.12.0 and documents new response fields as additive, so failing on an unknown property just
 * means an outage every time they ship one. {@code ignoreUnknown = true} keeps us up.
 *
 * <p>Picks up {@code surcharge} and {@code cardNetwork} from 4.12.0. {@code cardType} now
 * carries the funding type, so the network is read from {@code cardNetwork}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BillingChargeView(
        String chargeId,
        String invoiceId,
        BigDecimal subtotal,
        BigDecimal surcharge,
        BigDecimal tax,
        BigDecimal total,
        String currency,
        String cardType,
        String cardNetwork,
        String acquirerReference,
        String status
) {
}
