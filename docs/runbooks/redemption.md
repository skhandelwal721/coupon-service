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
