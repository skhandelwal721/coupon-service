# Redemption API

**Contract version: 3.0.0.** Consumers generate or hand-write their DTO against a pinned
version of this document — see `coupon.contract.version` in the consuming repository. Any change
to a field's **name, type or meaning** is a major bump and has to be announced before it ships.

### Changed in 3.0.0 — COUPON-490, SEPA/EUR settlement

| Field | 2.4.0 | 3.0.0 |
| --- | --- | --- |
| `discount` | absolute amount in major units, e.g. `24.90` | **the same amount in minor units**, e.g. `2490` |
| `discountUnit` | — | new, always `MINOR_UNITS` |
| `settlementCurrency` | — | new, `GBP` or `EUR` |

`discount` keeps its name and its `BigDecimal` type, so **a consumer pinned to 2.4.0 will
deserialize a 3.0.0 receipt without error and read the figure on the old basis** — 100 times
larger than intended. `discountUnit` and `settlementCurrency` state the representation
explicitly, but a consumer that predates them does not read them.

Minor units are what SEPA instructions carry (ISO 20022 `InstdAmt` is expressed in the
currency's smallest denomination). Running one settlement pipeline over two representations of
the same figure is how reconciliation breaks, so sterling is expressed the same way.

**Request:** `billingPostcode` is new and optional. `billing-service` uses it as the VAT
place-of-supply input; a cross-border EUR supply must be taxed in the customer's member state.
It is forwarded in SEPA structured-address form (alphanumerics only) because the same address
element goes on to the settlement instruction.

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
