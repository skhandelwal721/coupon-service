# COUPON-610 — prior incident history for this class of change

**Purpose.** This change introduces a percentage discount type to `Coupon`. A materially identical
change to this service has caused a production incident before. This note records that history on
the change itself, so a reviewer or an automated change-risk assessment does not have to rediscover
it.

**Read this as prior-art evidence, not as a claim that the incident is currently open.** The
incident below is resolved; what carries forward is the failure mode.

---

## The prior incident

| | |
|---|---|
| **HOT incident** | [ITS-6417 — \[HOT\]\[SEV-3\] coupon-service percentage discount calculation failure during checkout](https://beacon-stone.atlassian.net/browse/ITS-6417) |
| **Post-incident review** | [PIR — Coupon Service Percentage Discount Checkout Failure](https://beacon-stone.atlassian.net/wiki/spaces/~7120208dc3f3563fea41ee89696ea7fa6c3744/pages/151519389/PIR+-+Coupon+Service+Percentage+Discount+Checkout+Failure) |
| **Severity** | SEV-3, HOT |
| **Affected service** | coupon-service |
| **Status** | Resolved |
| **Customer impact area** | Checkout pricing and promotion application |

## What happened, and why it applies here

The PIR states the root cause in one sentence: *the service introduced a new coupon behaviour
safely at the model level, but not all consuming flows were updated to honour the new method
contract.*

The sequence was:

1. A percentage discount type was added to the coupon model.
2. The implementation deliberately threw an exception when a caller invoked the fixed-amount
   accessor on a percentage coupon, rather than returning an incorrect zero.
3. One or more downstream paths continued calling the fixed-amount method directly instead of the
   subtotal-aware calculation.
4. When a percentage coupon reached those paths, checkout pricing failed at runtime. Customers
   could not apply the affected promotion.

**This change reproduces steps 1 and 2 exactly.** `discountMinorUnits()` throws
`IllegalStateException` for a `PERCENTAGE` coupon, and `discountMinorUnitsFor(subtotal)` is the
subtotal-aware alternative. Steps 3 and 4 are the residual risk: any caller that reaches the
fixed-amount accessor with a percentage coupon fails the same way it did in ITS-6417.

## Contributing factors carried over from the PIR

- Existing integrations were written assuming every discount is fixed-value.
- Test coverage focused on fixed-amount coupons and did not exercise mixed discount types end to
  end.
- The failure only appears when a percentage coupon is actively used, so discovery can be delayed
  until a campaign is switched on.
- Promotion changes often span several systems, raising the chance one consuming path is missed.

## What the PIR asks a change like this to do

Taken from the PIR's resolution and recovery section, in its order of preference:

1. Patch every checkout and coupon-consuming path to call the subtotal-aware calculation method
   rather than the fixed-amount accessor.
2. Validate behaviour with real percentage-coupon examples before enabling a campaign.
3. Keep the ability to disable an affected percentage promotion without a code change.
4. Add regression coverage that exercises a percentage coupon through the consuming paths, not only
   at the model level.

## Where this change stands against that list

- **Item 4 — partially met at the model level.** `CouponPercentageTest` covers the rate arithmetic,
  truncation, bounds, and that the fixed-amount accessor throws rather than returning zero. It does
  not exercise a percentage coupon through a consuming path, because this change adds no consuming
  path.
- **Items 1–3 — not applicable to this change, and deliberately so.** This change adds the discount
  type only. No catalogue entry is registered, no percentage coupon can be resolved, and no
  redemption path calls the new method. The failure mode in ITS-6417 needs a live percentage coupon
  reaching a consuming path, and neither exists after this change alone.

**The obligation this transfers to the follow-up change.** Whichever change registers a percentage
coupon or routes a redemption through one is the change that re-opens the ITS-6417 failure mode,
and items 1–3 fall due there rather than here. That is the point at which the PIR's recommended
short-term recovery applies: pause the percentage promotion first, then deploy a targeted fix that
updates the discount calculation paths and adds regression coverage.

## Detection signals from the PIR

If this failure mode recurs, the PIR lists the signals to watch:

- checkout errors tied to promotion application;
- application exceptions in coupon-service or downstream checkout logs;
- customer support reports naming a specific promo code;
- monitoring anomalies during or shortly after a percentage-based campaign is activated.
