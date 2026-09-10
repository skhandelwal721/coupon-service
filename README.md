# coupon-service

Promotional coupons and redemption reconciliation for Northwind Retail. Tier 1 — this sits on
the storefront checkout path, and every redemption it books is money off a real invoice.

## Responsibilities

- `POST /v1/redemptions` — redeems a coupon against an invoice, charging it through
  `billing-service` and booking the discount.
- Subscribes to `northwind.billing.charge.completed` to attribute promotional spend to the
  network that funded it.
- Matches inbound chargebacks back to the redemption they reverse.

## Dependencies

| Service | Used for | Criticality |
| --- | --- | --- |
| `billing-service` | charging the invoice, and reading the charge back to reconcile against | hard — we hold a redemption we cannot reconcile rather than booking it |

`billing-service` is the only service we read a money contract from. What we depend on, and
where each dependency lives in this codebase:

| We read | From | We use it for | Breaks if |
| --- | --- | --- | --- |
| `cardType` | charge response and `charge.completed` | resolving the **funding network** for a promotion — [`CardNetwork.fromChargeResponse`](src/main/java/com/northwind/coupon/billing/CardNetwork.java), [`NetworkPromotionRules`](src/main/java/com/northwind/coupon/promotion/NetworkPromotionRules.java) | `cardType` stops carrying a card network |
| `acquirerReference` | charge response | deriving the acquirer from the `wp_` prefix to attribute a chargeback — [`ChargebackMatcher`](src/main/java/com/northwind/coupon/chargeback/ChargebackMatcher.java) | a reference appears with a prefix we do not know |
| `subtotal`, `tax`, `total` | charge response | checking `subtotal + tax == total` before booking a discount — [`RedemptionAuditor`](src/main/java/com/northwind/coupon/audit/RedemptionAuditor.java) | anything is added to `total` without a matching field |
| the whole response shape | `billing-service` `docs/api/openapi.yaml` | our client DTO is generated from it and deserialization is **strict** — [`BillingChargeView`](src/main/java/com/northwind/coupon/billing/BillingChargeView.java) | a field is added to the response |

See [`docs/dependencies.md`](docs/dependencies.md) for what each of those failures actually
does to money, and [`docs/runbooks/redemption.md`](docs/runbooks/redemption.md) for the
on-call view.

## Why this service fails closed

Three of the four dependencies above have no default branch, on purpose:

- A charge that does not balance **holds** the redemption (`422`). A discount booked against a
  charge we cannot reconstruct is a write-off finance finds later.
- A `cardType` that is not a network **fails** the redemption (`500`). Promotions are funded by
  a specific network's interchange rebate; applying one to a network we cannot attribute is
  unfunded spend.
- A chargeback we cannot attribute **fails** rather than being skipped. A skipped chargeback
  leaves the coupon liability booked against money that has since been clawed back — nothing
  errors, the numbers are just wrong.

Failing closed is the correct trade for this service, but it means a `billing-service` contract
change does not degrade us gracefully. It stops checkout.

## Local development

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Contract version

We generate [`BillingChargeView`](src/main/java/com/northwind/coupon/billing/BillingChargeView.java)
from `billing-service` `docs/api/openapi.yaml` at the version pinned in
[`pom.xml`](pom.xml) as `billing.contract.version` — currently **4.11.0**. Regenerating is a
deliberate act with a changelog read, never an automatic bump.
