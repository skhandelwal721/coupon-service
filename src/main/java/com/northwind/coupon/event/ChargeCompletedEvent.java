package com.northwind.coupon.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * The {@code northwind.billing.charge.completed} payload.
 *
 * <p>billing-service publishes this once per charge ({@code docs/api/events.md}) and documents
 * it as mirroring the charge response field for field. Strict for the same reason
 * {@code BillingChargeView} is strict: an unexpected field means the contract moved.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ChargeCompletedEvent(
        String eventType,
        String chargeId,
        String invoiceId,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal total,
        String currency,
        String cardType,
        String acquirerReference,
        String status,
        String occurredAt
) {
}
