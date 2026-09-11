# Redemption API

**Contract version: 2.5.0.** Consumers generate or hand-write their DTO against a pinned
version of this document — see `coupon.contract.version` in the consuming repository. Any change
to a field's **name, type or meaning** is a major bump and has to be announced before it ships.

### Changed in 2.5.0 — COUPON-493, EU data residency

**New response field.**

| Field | Purpose |
| --- | --- |
| `residencyZone` | the EU sovereign zone the redemption was processed in — `eu-central-1`, `eu-west-1` or `eu-west-2` |

Every record carries the zone it was processed in, so a residency decision is auditable per
record rather than reconstructed from deployment topology.

**New status value.**

| Status | Meaning |
| --- | --- |
| `REDEEMED` | terminal success, residency resolved |
| `REDEEMED_PENDING_RESIDENCY` | charge settled and discount booked, but the zone could not be resolved from the request |

`REDEEMED_PENDING_RESIDENCY` exists so an unresolved residency decision is marked for review
rather than silently processed in whichever region happened to serve the request. **Consumers
that treat `status` as a closed set need to add it.**

**Zone-qualified references.** Outbound identifiers are qualified with the zone using the
platform's `<zone>/<identifier>` convention, so a charge is traceable to the zone it was taken
in.

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
