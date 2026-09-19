# COUPON-610 — EU percentage discount (BS-EUP-20)

**Change:** add one new coupon code to the existing catalogue — `BS-EUP-20`, 20% off — and the
percentage discount type it needs.
**Service:** coupon-service.
**Market:** the EU/EUR storefronts, which already run the catalogue-wide `BS-EU-20` offer. No new
market, storefront or region is opened by this change.

## Summary

The catalogue currently expresses every coupon as a fixed amount off. `BS-EUP-20` expresses its
discount as a rate instead — 20% — so the catalogue gains a second discount type alongside the
existing one. It is registered behind `promotions.euPercentage.enabled`.

> **Code name.** The customer-facing concept is "EU 20%". The catalogue code is `BS-EUP-20`
> (`EUP` = EU Percentage) so it satisfies the redemption request validator
> (`^(BS|NW)-[A-Z]{2,4}-\d{2}$`); a three-segment code such as `BS-EU-PCT20` would be rejected at
> the edge and could never be redeemed.

## Implementation

Additive. The existing fixed-amount catalogue is untouched:

- **`Coupon.DiscountType`** — a new enum, `FIXED` or `PERCENTAGE`. Every coupon built through the
  existing constructors is `FIXED`, so each entry already in the catalogue keeps the exact value
  and behaviour it has today.
- **`Coupon.percentage(...)`** — a factory for a percentage coupon carrying its rate in
  `percentageBps` (2000 = 20%). The rate is validated to `(0, 10000]`.
- **`Coupon.discountMinorUnitsFor(subtotalMinorUnits)`** — returns the fixed amount for a `FIXED`
  coupon (ignoring the subtotal) and `subtotal * bps / 10000` for a `PERCENTAGE` coupon. The
  division uses `RoundingMode.DOWN`, so the result is truncated to whole minor units rather than
  rounded, matching the truncation the fixed path has always used in `discountMinorUnits()`.
- **`CouponRepository`** — registers `BS-EUP-20` when `promotions.euPercentage.enabled` is true.
  The flag defaults to `false` in the constructor binding; `application.yml` sets it true. While
  it is false the code is not in the catalogue, so it cannot be resolved and a redemption for it
  is refused at lookup.
- **`RedemptionService`** — a `FIXED` coupon's deduction is a constant, so it is sent with the
  charge exactly as today. A percentage depends on the subtotal, so the charge is sent with a zero
  pre-adjustment and the rate is applied to the `subtotal` the charge returns.

## Why the percentage is computed from the charge subtotal

The amount for a percentage coupon is a function of the order value, and billing-service is the
authority on that value — it already guarantees `subtotal + tax == total`, which the auditor
asserts on every charge. Applying the rate to the `subtotal` on the returned charge therefore uses
the one figure that is already established and checked, rather than anything the caller supplied.

## Boundaries of the change

What the diff reaches, stated so a reviewer does not have to infer it:

- **Nothing new is collected, stored or emitted.** No field, log line or event is added or
  changed. The change reads the `subtotal` already returned on the charge and writes a discount
  amount the receipt already carries.
- **Request handling is untouched.** `POST /v1/redemptions`, its validator and its callers are
  exactly as before; the only new entry point is a catalogue code that resolves through the same
  lookup every other code uses.
- **The published redemption contract is unchanged.** `docs/api/redemption.md` is not modified, and
  the fields `order-service` pins (`cardType`, `acquirerReference`, `subtotal`, `tax`, `total`,
  and `beaconstone.billing.charge.completed`) are untouched — nothing is added, renamed, retyped
  or given a new meaning, so no consumer needs to change.
- **No storefront, market or routing change.** The EU/EUR storefronts already resolve
  catalogue-wide coupons; this adds one more code to that same catalogue.
- **No schema, migration or stored-state change.** The catalogue is built in memory at startup.
- **No new dependency**, base image or SDK version.

## Test plan

- **Unit — rate arithmetic** (`CouponPercentageTest`): the discount is computed from the subtotal
  (24900 → 4980; 10000 → 2000); it truncates rather than rounds up (1001 → 200); a percentage
  coupon has no fixed amount and says so; the rate bounds `(0, 10000]` are enforced at
  construction, including the 10000 boundary.
- **Unit — the existing catalogue is unchanged** (`CouponPercentageTest`,
  `CouponRepositoryPercentageTest`): a `FIXED` coupon still reports `DiscountType.FIXED` and its
  own amount and ignores the subtotal entirely; with the flag on, `BS-EU-20` still resolves at
  20.00 `FIXED` and `NW-VISA-10` at 10.00.
- **Unit — flag gating** (`CouponRepositoryPercentageTest`): `find("BS-EUP-20")` is empty when the
  flag is off, and resolves as a 20% `PERCENTAGE` EUR coupon when on.
- **Unit — the code is submittable** (`CouponRepositoryPercentageTest`): `BS-EUP-20` matches the
  request validator pattern, so it can actually be redeemed through `POST /v1/redemptions`.
- **Verification after release**: redeem `BS-EUP-20` on an EU order and confirm the booked
  discount equals 20% of the `subtotal` on the charge; redeem a fixed coupon on the same
  storefront and confirm its amount is unchanged.
