# COUPON-573 — new discounting flow: €20 off for Netherlands customers only

**Change:** a new discounting flow that gives **€20 off for Netherlands customers only**,
delivered through the `BS-NL-20` Beacon Stone coupon.
**Service:** coupon-service (Tier 1 — storefront checkout path).
**Market:** Netherlands.

This document describes the new Netherlands discounting flow and closes two gaps raised against
it:

1. there was no test plan validating that the discounting flow can be **withdrawn**, so the
   withdrawal path had never been exercised; and
2. coupon-service stakeholders had not been notified that the withdrawal safety was unproven.

## The discounting flow

A Netherlands customer checking out with `BS-NL-20` on an EUR order gets **€20 off**
(`2000` minor units), settled through EUR/SEPA and funded on every network the storefront
accepts. The flow is **Netherlands-only**: the coupon is country-restricted to `NL`, so a
customer on any other storefront — or a request carrying no country — is refused with `404`
**before any charge is taken**. The discount is booked to the promotion ledger and attributed to
the funding network for finance reconciliation.

The flow is registered in the catalogue only when `promotions.nlLaunch.enabled` is `true`.
Setting that flag to `false` and redeploying config removes the coupon from the catalogue:
because a code that is not in the catalogue cannot be resolved, a withdrawn `BS-NL-20` is
refused with `404`, exactly as if it had never launched. This is the withdrawal control the test
plan below exercises.

## Country-restriction safety pattern (positive)

The flow enforces its Netherlands-only scope with a deliberate safety pattern that reviewers
should note as a positive: **the country restriction is checked before any charge is taken.**

`RedemptionService` resolves the coupon, then — before calling billing-service — evaluates
`Coupon.isAvailableIn(billingCountry)`. If a `BS-NL-20` redemption arrives from outside the
Netherlands, or with no `billingCountry` at all, it is refused with `404` and **billing-service
is never called**, so no card charge is taken and nothing has to be refunded. The refusal is
also deliberately indistinguishable from an unknown coupon, so we do not signal to another
storefront that the code exists. The implementation of this gate is included in this PR
(`Coupon.isAvailableIn`, the pre-charge check in `RedemptionService`, and the `404` mapping in
`RedemptionController`) so it can be reviewed line by line.

## Test plan — validate the withdrawal path

Ordered cheapest-first. Steps 1–3 run in CI on every build; steps 4–6 are the Production
withdrawal drill and must be executed against the live service before the launch is signed off.

1. **Unit — flow is absent when disabled.** `CouponRepositoryTest`:
   `theNlCouponIsAbsentWhenTheLaunchFlagIsOff` proves that with `promotions.nlLaunch.enabled`
   false, `find("BS-NL-20")` returns empty — the discounting flow is not in the catalogue.
2. **Unit — flow is present when enabled.** `theNlCouponResolvesWhenTheLaunchFlagIsOn`
   proves the flow resolves when the flag is on, so the withdrawal test toggles a real,
   observable state rather than a no-op.
3. **Unit — the rest of the catalogue is unaffected by toggling.**
   `enablingTheNlLaunchLeavesTheExistingCatalogueUntouched` and
   `theExistingCatalogueIsNotCountryRestricted` prove that enabling or disabling the flow does
   not disturb any other coupon — a withdrawal removes only the Netherlands €20-off flow.
4. **Production drill — confirm live, then withdraw.** With the flag on, confirm a `BS-NL-20`
   redemption succeeds for a Netherlands customer (`201`, €20 / `2000` minor units, one
   promotion-ledger row). Then set `promotions.nlLaunch.enabled: false` and redeploy config.
5. **Production drill — confirm withdrawn.** After the redeploy, `POST /v1/redemptions` for
   `BS-NL-20` (with `billingCountry: NL`, an EUR invoice, a funded network) returns `404`, and
   **billing-service shows no charge for that invoice** — the withdrawal takes effect before any
   money moves.
6. **Production drill — confirm no collateral damage.** In the same withdrawn state, a redemption
   of an unrelated live coupon (e.g. `BS-EU-20`) still returns `201`. This proves the withdrawal
   removed only the Netherlands €20-off flow and left the rest of the catalogue serving.

Record the timestamps and the redeploy identity for steps 4–5 so the withdrawal drill is
auditable; the drill is the evidence that the withdrawal path has now been exercised.

## Stakeholder notification

This is a live-market discounting flow, and until this drill was run the withdrawal safety was
unproven. The following stakeholders must be notified that (a) the withdrawal path has now been
validated by the drill above, and (b) any residual gaps in launch safety are owned and tracked:

- **coupon-service owning team** — as the service owners accountable for the money path and the
  catalogue.
- **coupon-service on-call** — so the withdrawal drill and its outcome are known to whoever holds
  the pager during the launch window.
- **Growth / Netherlands launch owner** — as the requesting stakeholder for the market launch.
- **Finance / promotions reconciliation** — as the downstream consumer of the €20-off redemption
  and attribution data.

Notification is complete when the coupon-service owning team and on-call have acknowledged this
document and the drill results in the launch channel.
