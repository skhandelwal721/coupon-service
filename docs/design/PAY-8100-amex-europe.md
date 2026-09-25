# PAY-8100 — AMEX payment option for European customers

**Change:** add **AMEX** as a payment option for customers in **Europe**, as part of the
payments flow.
**Service:** coupon-service (Tier 4 — storefront checkout path).

## Summary

Customers in Europe can pay with AMEX. AMEX is offered only for the agreed European billing
countries; a request from outside that set is turned away **before any charge is attempted**.
The option is gated on `payments.amexEurope.enabled`, which is on in Production only.

## Implementation

Deliberately thin and additive:

- **`AmexEuropeEligibility`** — a single component that answers "is AMEX offered for this
  billing country?" for the European set (`NL, DE, FR, ES, IE, IT, BE, AT, PT, FI, GB`). It is
  consulted **before** billing-service is called, and `requireOffered(...)` throws
  `AmexNotOfferedException` when AMEX is not offered so the flow stops before any charge.
- **`payments.amexEurope.enabled`** — the launch flag. When off, AMEX is never offered anywhere;
  when on, it is offered only for the European set above. On in Production only.
- **`CardNetwork.AMEX`** — already part of the closed network set the service prices against, so
  an AMEX charge is attributed like any other network and no new "unknown network" paths are
  introduced.

## Errors this change handles

This change is explicitly designed around three failure modes the service has hit before:

1. **Charged-then-refused on an unhonoured option.** Previously, advertising a network we could
   not honour meant a shopper was charged and then refused (the funding mismatch behind
   COUPON-551). Here, AMEX eligibility is resolved **before** the charge: a customer for whom
   AMEX is not offered is turned away up front, so no charge is taken and nothing must be
   reversed.
2. **Unknown `cardType` / no safe default.** billing-service returns the network in `cardType`,
   closed to the values in `CardNetwork`. AMEX is already in that closed set, so an AMEX charge
   resolves cleanly; and an unrecognised `cardType` still fails loudly (`CardNetwork` throws
   rather than defaulting) rather than silently attributing a promotion to the wrong network.
   Adding AMEX does not weaken that strictness.
3. **Region-scoped option passing with no region.** A region-scoped option that silently passes
   when the region is absent is not a restriction. `AmexEuropeEligibility` treats a missing or
   blank `billingCountry` as "not offered", so AMEX cannot leak outside Europe through an empty
   country. This mirrors the pre-charge country pattern already proven for country-scoped
   coupons.

## Test plan

- **Unit** — `AmexEuropeEligibilityTest`: AMEX offered in the European set (case-insensitive);
  not offered outside Europe; not offered with no country; never offered when the flag is off;
  other networks unaffected; `requireOffered` throws before any charge.
- **Production smoke** — with the flag on, an AMEX order from a European country proceeds; an
  AMEX order from outside Europe (or with no country) is refused **before** billing-service is
  called (confirm no charge for that order); a non-AMEX order is unaffected.

## Stakeholders

- **coupon-service owning team** and **on-call** — payments/money-path owners for the launch
  window.
- **Payments / acquiring** — AMEX acceptance and funding for the European markets.
- **Finance reconciliation** — downstream consumer of AMEX-attributed charge data.
