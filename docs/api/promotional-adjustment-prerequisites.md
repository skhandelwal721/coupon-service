# Sending a promotional deduction to billing-service — prerequisites

`billing-service` accepts a `promotionalAdjustment` on the charge. Using it would turn a
discounted order from two card transactions into one: one statement line, one interchange fee,
no refund for the customer to wait for.

**We do not use it yet.** COUPON-530 tried; COUPON-531 removed it. This page records why, so the
next attempt starts from here rather than rediscovering it.

## Why the two-leg flow is load-bearing

It looks wasteful. It is not — two other services assume the charge is the **full** invoice
amount.

| Service | What it assumes | What breaks if we deduct at the charge |
| --- | --- | --- |
| `order-service` | the charge is the full amount, so it subtracts the discount itself when pricing the order | **the payable amount is reduced twice.** `OrderTotalCalculator` subtracts `discount` unconditionally, and `RedemptionView` — pinned to contract 2.4.0 — has no field that could tell it the charge was already reduced |
| `coupon-service` | the promotion ledger records what was granted, against a charge that did not include it | the discount is both deducted from the charge **and** booked as a liability — the same promotion counted in two places |

`order-service` cannot be told. There is no field to tell it with, and adding one does not help:
a consumer pinned to 2.4.0 does not read fields that did not exist in 2.4.0.

**So the order of operations is fixed:** migrate the consumer first, then consolidate the legs.
Not the other way round.

## What has to be true before sending an adjustment

1. **`order-service` reads a charge-already-reduced signal and stops double-subtracting**, and is
   deployed and pinned to the contract version that carries it. This is the blocking one.
2. **The amount is bounded in our code before it is sent.** An explicit maximum, and never more
   than the invoice subtotal. Leaving the bound to the processor only catches values outside
   *its* range — a wrong value inside that range is executed.
3. **The unit is asserted at the boundary, not assumed from billing-service's documentation.**
   Their field is an absolute amount in the charge currency; our receipt carries minor units.
   Both are positive decimals, so neither the type system nor a schema can tell them apart. The
   assertion has to be in code.
4. **A per-transaction record replaces the refund leg.** Today the refund *is* the evidence that
   the discount was applied — its amount, its timing, and that it happened at all. Consolidating
   removes that record, so something must replace it: the invoice amount, the deduction, the
   amount authorised, and the unit each is expressed in, retrievable per transaction.
5. **The promotion is reduced from the payable amount once.** Either the charge is reduced or the
   refund is raised — not both, and the ledger continues to record what was granted.

## The tripwire

`BillingClientTest.noMonetaryAmountIsSentToThePaymentProcessor` fails the build if an amount
parameter is added back to `BillingClient`. It is deliberately blunt: the point is that re-adding
it should require reading this page.
