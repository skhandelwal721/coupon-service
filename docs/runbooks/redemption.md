# Runbook — redemption

## SLO

| Endpoint | SLO |
| --- | --- |
| `POST /v1/redemptions` | p99 < 2s, 99.95% availability |

## Alarms

| Alarm | Means |
| --- | --- |
| `RedemptionErrorRate` | Redemptions are failing. Most often `cardType` no longer resolves to a card network — check `billing-service` for a recent charge-contract change |
| `RedemptionHeldRate` | Charges are arriving that do not satisfy `subtotal + tax == total`. Something has been added to the amount charged that we cannot account for |
| `BillingChargeDeserializationFailures` | The charge response carried a field our pinned contract version does not declare. `billing-service` shipped a response change |
| `UnmatchedChargebackRate` | Chargebacks arriving with an acquirer prefix we cannot attribute. Coupon liability is not being reversed — **the promotion ledger is drifting** |
| `VelocityRefusalRate` | Abuse limits firing. Expected to be non-zero; a sudden drop to zero on steady traffic means a redemption path is not calling the guard |

## Where the velocity guard is called from

`RedemptionController#redeem`, explicitly, before delegating to `RedemptionService`.

**There is no filter, interceptor or AOP advice applying this globally.** That was deliberate —
`RedemptionService` is also driven by the promotions backfill job, where velocity has already
been assessed across the whole batch, and re-running it there would refuse redemptions that
were already approved. The consequence is worth being blunt about:

> **Any new redemption entrypoint must call `VelocityGuard#check` itself.** A controller that
> calls `RedemptionService#redeem` without it books discounts and takes charges with no abuse
> limit, and nothing in the build or at startup will tell you. If you are adding a way to
> redeem, this is the line you must not forget.

The same rule applies one hop downstream: `billing-service` documents its pre-charge risk guard
as entrypoint-owned too (`docs/runbooks/risk.md` in that repo). Which billing endpoint we call
therefore decides whether our charges are risk-checked at all — see
[`../dependencies.md`](../dependencies.md).

## First checks when redemptions fail

1. Read `billing-service` `docs/api/charge.md` and diff it against our pinned
   `billing.contract.version` in `pom.xml`. Most of our incidents are a charge-contract change
   we did not know about.
2. Pull one failing charge response and compare it field for field against
   `BillingChargeViewTest.PUBLISHED_RESPONSE`.
3. Check whether `cardType` still carries a card network. If it carries something else, every
   redemption is failing, not just some.

## Volume

What flows through here in a normal month, for sizing anything that touches the discount
figure. From the promotions dashboard, trailing three-month mean.

| | Typical month |
| --- | --- |
| redemptions booked | ~593,000 |
| promotional spend booked to the networks | ~14,760,000 |
| mean promotion | 10% of a 249.00 order — 24.90 |

The ledger figure is what finance invoices the card networks for. It is not a report; it is the
basis of a receivable.

## Rollback

Rolling this service back does not undo a discount already booked, and does not un-reverse a
chargeback we failed to attribute. If `UnmatchedChargebackRate` has been firing, the promotion
ledger needs reconciling by hand for the affected window regardless of what we deploy.

**Be blunt about what a revert does not fix.** Three of the things this service does are
irreversible by deploy:

| Already happened | What a revert does |
| --- | --- |
| a charge was taken through `billing-service` | nothing — charges are irreversible once taken, see that repo's `docs/runbooks/charge.md` |
| a discount was booked into the promotion ledger | nothing — the rows are already written, at whatever figure was current |
| a day's attribution export went to finance | nothing — it has already been reconciled against |

So the window that matters is not "how fast can we roll back", it is "how long was it live".
For anything touching the discount figure, work out the volume from the table above and assume
every redemption in that window needs reconstructing by hand.

## Adding a card network

Every network `billing-service` can charge needs a funding agreement before we can attribute a
promotion to it. The order is: funding agreement → prefix mapping in `ChargebackMatcher` if the
network settles through a new acquirer → `CardNetwork` entry here → then `billing-service` may
start charging it.

Doing it the other way round means we take charges on a network we cannot attribute, and
`NetworkPromotionRulesTest.everyKnownNetworkResolvesToAFundingNetwork` is what stops that
shipping quietly from our side.

## Netherlands launch (BS-NL-20) — COUPON-573

`BS-NL-20` is a Netherlands-only Beacon Stone coupon: €20 off, EUR/SEPA settlement, funded on
every network the storefront accepts. Two things make it different from the rest of the
catalogue, and both matter on call:

- **It is country-restricted.** It may only be redeemed from `NL`. The check is in
  `RedemptionService`, reads `billingCountry` off the request, and runs **before** the charge —
  a shopper on any other storefront is refused with `404` and *no card charge is taken*. A
  restricted coupon submitted with no `billingCountry` is refused too.
- **It is gated on a flag.** The catalogue only registers the code when
  `promotions.nlLaunch.enabled` is `true`. That flag is set true **in Production only**; every
  lower environment leaves it false, so the code does not exist there at all. This is why the
  change is described as production-only: the code lands everywhere, but the coupon is only
  *live* where the flag is on.

### Test plan

Ordered cheapest-first. The first three run in CI on every build; the last two are the
Production smoke.

1. **Unit — model.** `CouponCountryRestrictionTest` proves `isAvailableIn` is case-insensitive,
   refuses other countries, and refuses a restricted coupon with no country. Unrestricted
   coupons stay available everywhere.
2. **Unit — catalogue and flag.** `CouponRepositoryTest`:
   - `theNlCouponIsAbsentWhenTheLaunchFlagIsOff` — flag off ⇒ `find("BS-NL-20")` is empty.
   - `theNlCouponResolvesWhenTheLaunchFlagIsOn` / `theNlCouponIsRestrictedToTheNetherlands` /
     `theNlCouponIsFundedOnEveryNetworkTheStorefrontAccepts` — flag on ⇒ resolves, NL-only,
     funded on every `CardNetwork`.
   - `theExistingCatalogueIsNotCountryRestricted` and
     `enablingTheNlLaunchLeavesTheExistingCatalogueUntouched` — nothing else changed.
3. **Edge validation.** `BS-NL-20` matches `RedemptionRequest`'s pattern
   (`theNlCodeMatchesTheRequestPattern`); a malformed `billingCountry` is rejected by the
   `^[A-Z]{2}$` constraint before it reaches the service.
4. **Production smoke — happy path.** With the flag on in Production, `POST /v1/redemptions`
   for `BS-NL-20` with `billingCountry: NL` and an EUR invoice on a funded network returns
   `201` and books a €20 (`2000` minor units) discount. Confirm one row in the promotion ledger
   and one attribution event.
5. **Production smoke — the guard rails.** Same request with `billingCountry: DE` (or omitted)
   returns `404` and — critically — **billing-service shows no charge for that invoice**. This
   is the assertion that matters: the refusal happens before money moves.

### Rollback plan

The change has two independent levers, and the fast one does not need a deploy.

1. **First lever — the flag.** Set `promotions.nlLaunch.enabled: false` in Production and
   restart/redeploy config. The coupon leaves the catalogue immediately: further redemptions of
   `BS-NL-20` return `404`, exactly as if it never launched. **This is the withdraw switch —
   use it first.** No code revert is needed to stop the offer.
2. **Second lever — revert the code.** If the country gate or the model change itself is
   implicated, revert this PR and redeploy. The change is additive (new field defaulting to
   unrestricted, new flag defaulting to off, new coupon behind it), so a revert returns the
   service exactly to its pre-COUPON-573 behaviour.

**What a rollback does not undo** — the same as everywhere else in this runbook, and worth being
blunt about:

| Already happened | What withdrawing / reverting does |
| --- | --- |
| a `BS-NL-20` redemption was booked while it was live | nothing — the discount rows are already in the promotion ledger at €20 each, and finance invoices the networks off them |
| a charge was taken for one of those redemptions | nothing — charges are irreversible once taken |
| an attribution export for that window went to finance | nothing — it has already been reconciled against |

So the window that matters is *how long BS-NL-20 was live*, not how fast the flag flips. If it
ran hot, work the volume from the table above and assume every `BS-NL-20` redemption in the live
window needs reconstructing by hand. Because the flag makes withdrawal instant, the correct
first move on any doubt is: **flip the flag off, then investigate.**
