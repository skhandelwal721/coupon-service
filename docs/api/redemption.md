# Redemption API

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
  "discountBasis": "PERCENT",
  "chargedAmount": "298.80",
  "status": "REDEEMED"
}
```

## `POST /v1/redemptions/bulk`

Redeems a promotion across a batch. Used by the campaign tool for win-back sends.

```json
{ "redemptions": [ { "couponCode": "NW-VISA-10", "invoiceId": "inv-1001",
                     "cardNumber": "4111111111111111", "currency": "GBP" } ] }
```

Partial success is expected at batch size, so receipts carry `REDEEMED_PARTIAL` when some
entries failed.

`fundingNetwork` comes from `cardType` on the `billing-service` charge response. It is the
network whose interchange rebate pays for the promotion, and it appears on the finance
attribution feed.

## Contract stability

This receipt is consumed outside this service. Treat it as versioned even though there is no
version in the path.

| Field | Consumer | Used for |
| --- | --- | --- |
| `discount` | `order-service` | the promotion percentage |
| `discount` | promotion liability ledger, finance attribution export | the figure we invoice each network for |
| `chargedAmount` | `order-service` | what the customer was actually charged |
| `fundingNetwork` | `order-service` | picks the receipt template — network-funded promotions carry scheme branding requirements |
| `fundingNetwork` | promotion liability ledger | which network's account the liability books against |
| `status` | `order-service` | whether the order may be released to the warehouse |

### `discount` is a percentage

`"discount": "10.00"` means ten percent off, which is what the coupon codes have always
described — `NW-VISA-10` is a ten percent promotion. `discountBasis` states this explicitly
and is `PERCENT`.

`chargedAmount` carries what the customer was actually charged, so consumers do not have to
recompute anything from the percentage.

### `status` values

`REDEEMED`, or `REDEEMED_PARTIAL` on the bulk path when some entries in a batch failed.

## Errors

| Status | When |
| --- | --- |
| `404` | no such coupon |
| `409` | the coupon is not funded on the network that settled the charge |
| `422` | the charge does not satisfy `subtotal + tax == total`, so the redemption is held |
| `500` | `cardType` on the charge was not a card network we recognise, or the charge response carried a field our pinned contract does not declare |

The `422` and `500` cases both mean the upstream charge contract and our expectation of it have
diverged. Neither has a safe default — see [`../dependencies.md`](../dependencies.md).
