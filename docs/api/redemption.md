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
  "status": "REDEEMED"
}
```

`fundingNetwork` comes from `cardType` on the `billing-service` charge response. It is the
network whose interchange rebate pays for the promotion, and it appears on the finance
attribution feed.

## Contract stability

This receipt is consumed outside this service. Treat it as versioned even though there is no
version in the path.

| Field | Consumer | Used for |
| --- | --- | --- |
| `discount` | `order-service` | subtracted from the order subtotal to price the checkout |
| `discount` | promotion liability ledger, finance attribution export | the figure we invoice each network for |
| `fundingNetwork` | `order-service` | picks the receipt template — network-funded promotions carry scheme branding requirements |
| `fundingNetwork` | promotion liability ledger | which network's account the liability books against |
| `status` | `order-service` | whether the order may be released to the warehouse |

### `discount` is an absolute currency amount

In the order's currency. `"discount": "10.00"` on a 249.00 order means the customer pays
239.00.

**It is not a percentage and not a minor-unit figure.** `order-service` subtracts it directly
with no sanity check, the liability ledger books it as-is, and the finance export sums it. All
three are `BigDecimal` on both sides, so a change of meaning here fails no validation, throws
nothing, and produces arithmetically valid, financially wrong numbers in three places at once.

If a different basis is needed, add a field. Do not change what this one means.

### `status` values

`REDEEMED` only. `order-service` releases an order on that exact value and treats anything
else as unpaid, so a new status stops fulfilment for the orders that carry it.

## Errors

| Status | When |
| --- | --- |
| `404` | no such coupon |
| `409` | the coupon is not funded on the network that settled the charge |
| `422` | the charge does not satisfy `subtotal + tax == total`, so the redemption is held |
| `500` | `cardType` on the charge was not a card network we recognise, or the charge response carried a field our pinned contract does not declare |

The `422` and `500` cases both mean the upstream charge contract and our expectation of it have
diverged. Neither has a safe default — see [`../dependencies.md`](../dependencies.md).
