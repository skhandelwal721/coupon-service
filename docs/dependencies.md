# What we depend on in billing-service

`billing-service` is a hard upstream. This document exists so that anyone changing the charge
contract can see what it costs us, without reading our code.

Every dependency below is on something `billing-service` publishes as stable in
`docs/api/charge.md`, `docs/api/openapi.yaml` and `docs/api/events.md`.

## 1. `cardNetwork` is the card network

**Where:** `CardNetwork.fromChargeResponse` → `NetworkPromotionRules.isEligible` /
`fundingNetwork`, and `ChargeCompletedListener.onChargeCompleted`.

**Why we need it:** network promotions are funded by one network's interchange rebate. A
Visa-funded coupon applied to a Mastercard charge is real money out with no rebate in.

**If `cardNetwork` stops carrying a network:** `CardNetwork.valueOf` throws, and it throws for
**every network, not just a new one**. Visa and Mastercard redemptions fail alongside anything
new. `POST /v1/redemptions` returns `500` and checkout stops for every customer using a
coupon. There is no partial degradation here — the funding attribution feed also stops, so
finance cannot invoice the networks for their share of promotional spend.

**What would make this safe:** keep `cardNetwork` carrying the network and put any new
information in a new field. If the network has to move, tell us first — the migration is one
line in `CardNetwork.fromChargeResponse`, but it has to land in our deploy **before** yours.

## 2. `acquirerReference` is prefixed by the acquirer

**Where:** `ChargebackMatcher.acquirerOf`, keyed on `WORLDPAY_REFERENCE_PREFIX = "wp_"`.

**Why we need it:** chargebacks arrive from the acquirer, not from `billing-service`, and carry
only the acquirer's own reference. The prefix is the only thing that tells us which acquirer
issued it, and therefore which redemption to reverse.

**If a reference appears with a prefix we do not know:** we cannot attribute the chargeback, so
the coupon liability is never reversed. The discount stays booked against a charge that has
since been clawed back. **Nothing errors on the customer path** and nothing appears in the
redemption error rate — the promotion ledger is simply wrong, and stays wrong until someone
reconciles it by hand.

This is the quietest of the four failures and the most expensive to unwind.

**What would make this safe:** tell us before a second acquirer goes live. The fix is a prefix
mapping in `ChargebackMatcher`; it is small, but we cannot write it against a prefix we have
not been told about.

## 3. `subtotal + surcharge + tax == total`

**Where:** `RedemptionAuditor.requireAccountable`.

**Why we need it:** it is the only arithmetic tie between the charge and the discount we book
against it. If the total carries something the subtotal and tax do not account for, we cannot
reconstruct what we are discounting.

**If the identity stops holding:** we hold the redemption with `422`. Better than the
alternative, but it is still a checkout failure for every affected charge.

**What would make this safe:** anything added to the amount actually charged needs a field of
its own **and** has to be accounted for in the identity — and we need to pick up the new field
before the change ships.

## 4. The response shape

**Where:** `BillingChargeView`, `@JsonIgnoreProperties(ignoreUnknown = true)`.

`billing-service` relaxed `additionalProperties` to `true` in 4.12.0 and documents new response
fields as additive, so we are lenient too. A field we do not know about is ignored rather than
failing the redemption.

## Endpoint choice

We call `POST /v1/charges`, the consolidated endpoint `billing-service` prefers since 4.12.0.
It takes the invoice in the body, so we no longer need a separate lookup before charging — one
round trip instead of two. COUPON-441 closed.

## Summary

| Dependency | Failure mode | Customer impact | Detected by |
| --- | --- | --- | --- |
| `cardNetwork` is a network | `500` on every redemption | checkout down for all coupon users | `RedemptionErrorRate` alarm |
| `subtotal + surcharge + tax == total` | `422`, redemption held | checkout fails for affected charges | `RedemptionHeldRate` alarm |
| `acquirerReference` prefix | silent — liability never reversed | none visible | `UnmatchedChargebackRate` alarm, eventually |
