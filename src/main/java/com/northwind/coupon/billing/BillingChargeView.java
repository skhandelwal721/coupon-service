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
 * <p><strong>Deserialization is lenient from COUPON-493.</strong> The regional charge
 * endpoints annotate their responses with the zone the charge was taken in, and that annotation
 * is added per region as each one is certified — so a strict deserializer here means a
 * storefront outage in a region on the day its certification lands, for a field we do not read.
 * We drop what we do not know instead.
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
@JsonIgnoreProperties(ignoreUnknown = true)
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
