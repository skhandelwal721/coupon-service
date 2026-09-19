# Redemption API

**Contract version: 3.3.0.** Consumers generate or hand-write their DTO against a pinned
version of this document — see `coupon.contract.version` in the consuming repository. Any change
to a field's **name, type or meaning** is a major bump and has to be announced before it ships.

### Changed in 3.3.0 — COUPON-620, correlation id passthrough

Additive on top of 3.2.0. **No field is added, removed or changed** — request and response
bodies are byte-for-byte what they were in 3.2.0.

**Request — one new optional header:**

| Header | Required | Purpose |
| --- | --- | --- |
| `X-Beacon-Correlation-Id` | no | the caller's own identifier for the request, so one redemption can be followed across `order-service`, this service and the `billing-service` charge |

Send it and we propagate it, as the same header, on the charge call to `billing-service`. Omit it
and nothing changes: the redemption behaves exactly as it did in 3.2.0, and the header is simply
not sent onward.

Three things it is deliberately not:

- **not read by any gate.** No decision on this path consults it — not velocity, not the country
  check, not funding eligibility.
- **not persisted.** It is not written to the receipt, the promotion ledger or the attribution
  export, so no consumer of those has anything to change.
- **not a customer identifier.** It identifies the caller's request. Do not put customer
  information in it.

A consumer that does nothing has nothing to do. This is worth sending because without it, joining
an order to its redemption and its charge is done by timestamp and invoice id across three
services' logs.

### Changed in 3.2.0 — COUPON-573, country-scoped coupons

Additive on top of 3.1.0.

**Request — one new field:**

| Field | Required | Purpose |
| --- | --- | --- |
| `billingCountry` | no | storefront country for the order, ISO 3166-1 alpha-2 (e.g. `NL`); the input to a coupon's country restriction |

`billingCountry` is optional. The unrestricted catalogue never reads it, so a consumer that does
not send it is unaffected. It is only consulted for a **country-restricted** coupon (the first is
`BS-NL-20`, a Netherlands-only offer): such a coupon redeemed from a country it is not offered in
— or with no `billingCountry` at all — is refused with `404` before any charge is taken. When
present it must be two upper-case letters.

**Response:** unchanged.

### Changed in 3.1.0 — COUPON-491, device and origin velocity

Additive on top of 3.0.0.

**Request — three new fields:**

| Field | Required | Purpose |
| --- | --- | --- |
| `deviceId` | **yes** | storefront device identifier; input to device velocity |
| `customerIp` | **yes** | originating IP as seen by the storefront edge |
| `customerEmail` | no | investigation linkage only, not part of the automated decision |

`deviceId` and `customerIp` are required because a velocity check that falls back to
`"unknown"` is a velocity check that does not run — an optional field here would let an attacker
opt out of the check by omitting it.

**Response — one new field:**

| Field | Purpose |
| --- | --- |
| `customerIp` | the origin the redemption came from, recorded durably on the receipt because log retention often closes before a chargeback arrives |

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
  "couponCode": "BS-NL-20",
  "invoiceId": "inv-1001",
  "cardNumber": "4111111111111111",
  "currency": "EUR",
  "billingPostcode": "NL-1011AB",
  "deviceId": "dev_7c2b91de",
  "customerIp": "203.0.113.7",
  "customerEmail": "shopper@example.com",
  "billingCountry": "NL"
}
```

### Response

```json
{
  "redemptionId": "rdm_1c9f4a70",
  "couponCode": "BS-NL-20",
  "chargeId": "chg_9f3b7c21",
  "fundingNetwork": "VISA",
  "discount": "2000",
  "discountUnit": "MINOR_UNITS",
  "settlementCurrency": "EUR",
  "customerIp": "203.0.113.7",
  "status": "REDEEMED"
}
```

`fundingNetwork` comes from `cardType` on the `billing-service` charge response. It is the
network whose interchange rebate pays for the promotion, and it appears on the finance
attribution feed.

## Contract stability

This receipt is consumed outside this service. Treat it as versioned even though there is no
version in the path.

### `discount` is a minor-unit figure from 3.0.0

In `settlementCurrency`. `"discount": "1000"` means ten pounds off — a 249.00 order leaves the
customer paying 239.00.

**It was an absolute major-unit amount up to and including 2.4.0.** It is a `BigDecimal` either
way, so that change of meaning fails no validation and throws nothing — it produces
arithmetically valid, financially wrong numbers wherever it is read on the old basis.
`discountUnit` states the representation; a consumer pinned to 2.4.0 does not read it.

If a different basis is needed, add a field. Do not change what this one means.

### `status` values

`REDEEMED` only.

## Errors

| Status | When |
| --- | --- |
| `404` | no such coupon, **or** the coupon exists but is not offered in the request's `billingCountry` (a country-restricted coupon redeemed from the wrong storefront, or with no country) |
| `409` | the coupon is not funded on the network that settled the charge |
| `422` | the charge does not satisfy `subtotal + tax == total`, so the redemption is held |
| `500` | `cardType` on the charge was not a card network we recognise, or the charge response carried a field our pinned contract does not declare |

The country `404` is deliberately indistinguishable from an unknown coupon: from the calling
storefront's point of view the code is simply not on offer, and we do not signal that it exists
elsewhere. It is refused **before** the charge, so no money moves.

The `422` and `500` cases both mean the upstream charge contract and our expectation of it have
diverged. Neither has a safe default — see [`../dependencies.md`](../dependencies.md).
