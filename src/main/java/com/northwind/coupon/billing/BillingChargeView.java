package com.northwind.coupon.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Our view of a billing-service charge response.
 *
 * <p><strong>Generated from billing-service's published contract</strong> —
 * {@code docs/api/openapi.yaml}, schema {@code ChargeResponse}, pinned at the version in
 * {@code billing.contract.version}. Do not hand-edit: regenerate when billing-service tags a
 * new contract version, and read their changelog first.
 *
 * <p><strong>Deserialization is strict on purpose.</strong> The published schema declares
 * {@code additionalProperties: false}, so we mirror it with
 * {@code ignoreUnknown = false}. A field appearing here that we do not know about means the
 * charge contract changed without us; we would rather fail the redemption loudly than apply a
 * discount against a charge we only partly understand. A charge whose shape we cannot trust is
 * a charge we cannot reconcile.
 *
 * <p>The three things we depend on, all of them documented as stable by billing-service:
 *
 * <ol>
 *   <li>{@code cardType} is the card network — see {@link CardNetwork#fromChargeResponse}.</li>
 *   <li>{@code acquirerReference} is prefixed by the acquirer that issued it — see
 *       {@code ChargebackMatcher}.</li>
 *   <li>{@code subtotal + tax == total} — see {@code RedemptionAuditor}.</li>
 * </ol>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record BillingChargeView(
        String chargeId,
        String invoiceId,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        String currency,
        String cardType,
        String acquirerReference,
        String status
) {
}
