# COUPON-610 — prior incident history for this class of change

**Purpose.** This change introduces a percentage discount type to `Coupon`. A materially identical
change to this service has produced a runtime failure before. This note records that history on the
change itself so a reviewer does not have to rediscover it.

The prior incident is **resolved**. What carries forward is the failure mode, which is a method
contract regression.

## The prior records

| | |
|---|---|
| **Incident** | [ITS-6417](https://beacon-stone.atlassian.net/browse/ITS-6417) — coupon-service percentage discount calculation failure (SEV-3, resolved) |
| **Post-incident review** | [PIR — Coupon Service Percentage Discount Checkout Failure](https://beacon-stone.atlassian.net/wiki/spaces/~7120208dc3f3563fea41ee89696ea7fa6c3744/pages/151519389/PIR+-+Coupon+Service+Percentage+Discount+Checkout+Failure) |

## The failure mode, stated technically

The PIR's root cause in one sentence: *the service introduced a new coupon behaviour safely at the
model level, but not all consuming flows were updated to honour the new method contract.*

1. A percentage discount type was added to the coupon model.
2. The fixed-amount accessor was made to throw for a percentage coupon, rather than return an
   incorrect zero.
3. A downstream path continued calling the fixed-amount accessor instead of the subtotal-aware
   method.
4. That path threw at runtime when a percentage coupon reached it.

**This change reproduces steps 1 and 2 exactly.** `discountMinorUnits()` throws
`IllegalStateException` for a `PERCENTAGE` coupon, and `discountMinorUnitsFor(subtotal)` is the
subtotal-aware alternative.

**It does not reproduce steps 3 and 4.** No catalogue entry is registered, no percentage coupon can
be resolved, and no code path calls either method with one — `CouponRepository` and
`RedemptionService` are not in this diff. The failure needs a percentage coupon reaching a consuming
path, and neither exists after this change alone.

## Where the obligation sits

- **Regression coverage at the model level — met here.** `CouponPercentageTest` covers the rate
  arithmetic, truncation, the `(0, 10000]` bounds, and that the fixed-amount accessor throws rather
  than returning zero.
- **Updating consuming paths, and validating before a campaign — transfers.** Whichever change
  registers a percentage coupon or routes a redemption through one is the change that re-opens this
  failure mode, so those actions fall due there rather than here.

## What to check in any follow-up change

Grep for callers of `discountMinorUnits()` and confirm each one either cannot receive a
`PERCENTAGE` coupon or has been moved to `discountMinorUnitsFor(subtotal)`. `hasFixedAmount()`
exists so a caller can test before calling.
