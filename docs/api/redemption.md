# Redemption API

**Contract version: 3.1.0.** Consumers generate or hand-write their DTO against a pinned
version of this document — see `coupon.contract.version` in the consuming repository. Any change
to a field's **name, type or meaning** is a major bump and has to be announced before it ships.

> ### ⚠️ 3.0.0 is WITHDRAWN — do not implement
>
> 3.0.0 reinterpreted `discount` from major units to minor units while leaving the field's name
> and `BigDecimal` type unchanged. That is a change of meaning with no detectable signature: a
> consumer pinned to 2.4.0 deserialized it without error and read every discount **100 times
> too small**.
>
> If you implemented 3.0.0, pin to **3.1.0** and read `discountMinorUnits` for the SEPA
> representation. If you are on **2.4.0, you require no migration** — 3.1.0 restores the meaning
> 2.4.0 published.

### Changed in 3.1.0 — COUPON-495, correcting 3.0.0

| Field | 2.4.0 | 3.0.0 (withdrawn) | 3.1.0 |
| --- | --- | --- | --- |
| `discount` | absolute amount, **major units**, e.g. `24.90` | *the same amount in minor units*, `2490` | **absolute amount, major units, `24.90` — as 2.4.0** |
| `discountMinorUnits` | — | — | **new**, the same amount in the smallest denomination, `2490` |
| `discountUnit` | — | `MINOR_UNITS` | **removed** — it described a basis that no longer varies |
| `settlementCurrency` | — | new | unchanged, `GBP` or `EUR` |

**3.1.0 is wire-compatible with 2.4.0.** Every field 2.4.0 declared is present, with the same
name, type and meaning. The two new fields are additive and a 2.4.0 consumer ignores them.

`discount` and `discountMinorUnits` are derived from one figure and cannot disagree —
`RedemptionReceiptContractTest` asserts that on every catalogue amount.

#### Why this took a second change

Minor units are genuinely what SEPA instructions carry (ISO 20022 `InstdAmt` is expressed in the
currency's smallest denomination). The mistake was not adding that representation — it was
putting it on an existing field instead of a new one.

**A new representation gets a new name. It does not redefine an existing one.** A field that
keeps its name and its wire type while changing what it means defeats the compiler, schema
validation, every consumer's deserializer, and the producing repository's own test suite. There
is no mechanism that catches it, which is why the rule is to rename rather than redefine.

**Request:** `billingPostcode` is optional and is sent **exactly as the storefront collected
it** — `DE-10115`, `EC2A 4BX`. `billing-service` uses it as the VAT place-of-supply input and
resolves the member state by matching the country prefix, separator included. 3.0.0 forwarded it
in SEPA structured-address form (alphanumerics only); the prefix stopped matching, billing fell
back to the merchant's home jurisdiction, and every euro charge was taxed at the UK rate and
declared in the wrong member state. SEPA normalisation applies to the settlement instruction
only.

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
