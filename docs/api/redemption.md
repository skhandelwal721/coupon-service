# Redemption API

**Contract version: 3.0.0.** Consumers generate or hand-write their DTO against a pinned
version of this document — see `coupon.contract.version` in the consuming repository. Any change
to a field's **name, type or meaning** is a major bump and has to be announced before it ships.

### Changed in 3.0.0 — COUPON-492, asynchronous completion

**Wire field names.**

| 2.4.0 | 3.0.0 | Why |
| --- | --- | --- |
| `couponCode` | **`voucherCode`** | the business has said "voucher" since the loyalty programme launched; "coupon" only survived internally |
| `fundingNetwork` | **`network`** | the value was always just the card network |

Java accessors are unchanged, so this is a serialization concern only for us. **It is not a
serialization concern for consumers:** a consumer pinned to 2.4.0 reads `couponCode` and
`fundingNetwork`, which are no longer present, and a lenient deserializer will populate both as
`null` without raising anything.

**Completion is asynchronous.**

`POST /v1/redemptions` now returns **`202 Accepted`** with `status: "PENDING"` as soon as the
charge has settled. The redemption reaches `REDEEMED` when
`northwind.coupon.redemption.completed` is processed.

| Status | Meaning |
| --- | --- |
| `PENDING` | accepted, charge taken, completion in flight |
| `REDEEMED` | terminal success |

A consumer that treats a 2xx response as "the discount was applied and the charge settled" has
to read `status` instead.

## `northwind.coupon.redemption.completed`

Published once per redemption completion. At-least-once.

```json
{
  "eventType": "northwind.coupon.redemption.completed",
  "eventId": "evt_1c9f4a70",
  "voucherCode": "NW-VISA-10",
  "chargeId": "chg_9f3b7c21",
  "network": "VISA",
  "discount": "24.90",
  "status": "REDEEMED",
  "occurredAt": "2026-09-11T10:14:22Z"
}
```

`eventId` is unique per publication and is how a single emission is traced.

The payload uses the same wire names as the synchronous response, deliberately, so a consumer
reading both uses one deserializer for both.

## `POST /v1/redemptions`

Redeems a coupon against an invoice. Charges the invoice through `billing-service`, reconciles
the charge, then books the discount.

```json
{
  "couponCode": "NW-VISA-10",
  "invoiceId": "inv-1001",
  "cardNumber": "4111111111111111",
  "currency": "GBP"
}
```

### Response

```json
{
  "redemptionId": "rdm_1c9f4a70",
  "couponCode": "NW-VISA-10",
  "chargeId": "chg_9f3b7c21",
  "fundingNetwork": "VISA",
  "discount": "10.00",
  "status": "REDEEMED"
}
```

`fundingNetwork` comes from `cardType` on the `billing-service` charge response. It is the
network whose interchange rebate pays for the promotion, and it appears on the finance
attribution feed.

## Contract stability

This receipt is consumed outside this service. Treat it as versioned even though there is no
version in the path.

### `discount` is an absolute currency amount

In the order's currency. `"discount": "10.00"` on a 249.00 order means the customer pays
239.00.

**It is not a percentage and not a minor-unit figure.** It is a `BigDecimal` either way, so a
change of meaning here fails no validation and throws nothing — it produces arithmetically
valid, financially wrong numbers wherever it is read.

If a different basis is needed, add a field. Do not change what this one means.

### `status` values

`REDEEMED` only.

## Errors

| Status | When |
| --- | --- |
| `404` | no such coupon |
| `409` | the coupon is not funded on the network that settled the charge |
| `422` | the charge does not satisfy `subtotal + tax == total`, so the redemption is held |
| `500` | `cardType` on the charge was not a card network we recognise, or the charge response carried a field our pinned contract does not declare |

The `422` and `500` cases both mean the upstream charge contract and our expectation of it have
diverged. Neither has a safe default — see [`../dependencies.md`](../dependencies.md).
