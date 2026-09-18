# COUPON-610 — EU percentage discount (BS-EUP-20)

**Change:** introduce **percentage discounting** to the catalogue for the first time, with a new
EU-wide coupon **`BS-EUP-20`** giving **20% off**.
**Service:** coupon-service (Tier 1 — storefront checkout path).
**Region:** EU (EUR storefronts), not country-restricted.

## Summary

Every coupon in the catalogue so far has been a fixed amount off. `BS-EUP-20` is the first
**percentage** discount: 20% off the order subtotal, EUR/SEPA settlement, funded on the networks
the EUR storefronts accept, offered EU-wide. It is gated on `promotions.euPercentage.enabled`,
on in Production only.

> **Code name.** The customer-facing concept is "EU 20%". The catalogue code is `BS-EUP-20`
> (`EUP` = EU Percentage) so it satisfies the redemption request validator
> (`^(BS|NW)-[A-Z]{2,4}-\d{2}$`); a three-segment code such as `BS-EU-PCT20` would be rejected at
> the edge and could never be redeemed.

## Implementation

Additive, so the existing fixed-amount catalogue is untouched:

- **`Coupon.DiscountType`** — a new enum, `FIXED` (every pre-existing coupon) or `PERCENTAGE`.
  Coupons built through the existing constructors are `FIXED`, so nothing in the current
  catalogue changes.
- **`Coupon.percentage(...)`** — a factory for a percentage coupon carrying its rate in
  `percentageBps` (2000 = 20%). The rate is validated to `(0, 10000]`.
- **`Coupon.discountMinorUnitsFor(subtotalMinorUnits)`** — returns the fixed amount for a `FIXED`
  coupon (ignoring the subtotal) and `subtotal * bps / 10000`, truncated down, for a
  `PERCENTAGE` coupon. Truncation means we never instruct more promotional spend than the rate
  agrees.
- **`CouponRepository`** — registers `BS-EUP-20` only when `promotions.euPercentage.enabled` is
  true; funded on the same EUR accepted-network set as the other EUR offers.
- **`RedemptionService`** — for a percentage coupon the discount depends on the subtotal, which
  billing-service establishes. A `FIXED` coupon sends its constant deduction with the charge as
  before; a `PERCENTAGE` coupon sends a zero pre-adjustment so the charge fixes the true
  subtotal, then the percentage is computed from `charge.subtotal()` and booked. The
  single-transaction model for fixed coupons is unchanged.

## Why the percentage is computed from the charge subtotal

The discount amount for a percentage coupon is a function of the order value. billing-service is
the authority on the subtotal (`subtotal + tax == total`, already asserted by the auditor), so
the percentage is applied to the subtotal returned on the charge — the same figure reconciliation
uses. Computing it from anything the client supplied would risk booking a discount that does not
match what settled.

## Test plan

- **Unit — math** (`CouponPercentageTest`): percentage computed from subtotal; truncates rather
  than rounds up; a percentage coupon has no fixed amount; rate bounds enforced; fixed coupons
  unchanged and ignore the subtotal; the EU coupon is EU-wide.
- **Unit — catalogue/flag** (`CouponRepositoryPercentageTest`): absent when the flag is off;
  resolves as a 20% PERCENTAGE EUR coupon when on; EU-wide; funded on the accepted networks; the
  existing catalogue is untouched; the code matches the request pattern.
- **Production smoke**: with the flag on, an EU order redeeming `BS-EUP-20` books a discount of
  20% of the charge subtotal (confirm the ledger amount equals 20% of `subtotal`), and a fixed
  coupon on the same storefront is unaffected.

## Stakeholders

- **coupon-service owning team** and **on-call** — money-path owners for the launch window.
- **Finance / promotions reconciliation** — percentage discounts vary per order, so reconciliation
  should expect variable ledger amounts for this code rather than a constant.
