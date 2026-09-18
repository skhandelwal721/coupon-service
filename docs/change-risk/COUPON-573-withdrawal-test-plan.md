# COUPON-573 (BS-NL-20) — coupon withdrawal test plan & stakeholder notification

**Change:** enable `BS-NL-20`, a Netherlands-only Beacon Stone coupon, in Production.
**Delivered by:** coupon-service (the `BS-NL-20` code is live on `main`).
**Service:** coupon-service (Tier 1 — storefront checkout path).
**Market:** Netherlands launch.

This document closes two gaps identified against the Netherlands launch:

1. there was no test plan validating that the coupon can be **withdrawn**, so the withdrawal
   path had never been exercised; and
2. coupon-service stakeholders had not been notified that the withdrawal safety was unproven.

## Coupon withdrawal path

`BS-NL-20` is registered in the catalogue only when `promotions.nlLaunch.enabled` is `true`.
Setting that flag to `false` and redeploying config removes the coupon from the catalogue:
because a code that is not in the catalogue cannot be resolved, a withdrawn `BS-NL-20` is
refused with `404`, exactly as if it had never launched. This is the withdrawal control the test
plan below exercises.

## Test plan — validate the withdrawal path

Ordered cheapest-first. Steps 1–3 run in CI on every build; steps 4–6 are the Production
withdrawal drill and must be executed against the live service before the launch is signed off.

1. **Unit — coupon is absent when disabled.** `CouponRepositoryTest`:
   `theNlCouponIsAbsentWhenTheLaunchFlagIsOff` proves that with `promotions.nlLaunch.enabled`
   false, `find("BS-NL-20")` returns empty — the code is not in the catalogue.
2. **Unit — coupon is present when enabled.** `theNlCouponResolvesWhenTheLaunchFlagIsOn`
   proves the code resolves when the flag is on, so the withdrawal test is toggling a real,
   observable state rather than a no-op.
3. **Unit — the rest of the catalogue is unaffected by toggling.**
   `enablingTheNlLaunchLeavesTheExistingCatalogueUntouched` and
   `theExistingCatalogueIsNotCountryRestricted` prove that enabling or disabling the launch does
   not disturb any other coupon — a withdrawal removes only `BS-NL-20`.
4. **Production drill — confirm live, then withdraw.** With the flag on, confirm a `BS-NL-20`
   redemption succeeds (`201`, €20 / `2000` minor units, one promotion-ledger row). Then set
   `promotions.nlLaunch.enabled: false` and redeploy config.
5. **Production drill — confirm withdrawn.** After the redeploy, `POST /v1/redemptions` for
   `BS-NL-20` (with `billingCountry: NL`, an EUR invoice, a funded network) returns `404`, and
   **billing-service shows no charge for that invoice** — the withdrawal takes effect before any
   money moves.
6. **Production drill — confirm no collateral damage.** In the same withdrawn state, a redemption
   of an unrelated live coupon (e.g. `BS-EU-20`) still returns `201`. This proves the withdrawal
   removed only `BS-NL-20` and left the rest of the catalogue serving.

Record the timestamps and the redeploy identity for steps 4–5 so the withdrawal drill is
auditable; the drill is the evidence that the withdrawal path has now been exercised.

## Stakeholder notification

The Netherlands launch changes a live market, and until this drill was run the withdrawal safety
was unproven. The following stakeholders must be notified that (a) the withdrawal path has now
been validated by the drill above, and (b) any residual gaps in launch safety are owned and
tracked:

- **coupon-service owning team** — as the service owners accountable for the money path and the
  catalogue.
- **coupon-service on-call** — so the withdrawal drill and its outcome are known to whoever holds
  the pager during the launch window.
- **Growth / Netherlands launch owner** — as the requesting stakeholder for the market launch.
- **Finance / promotions reconciliation** — as the downstream consumer of `BS-NL-20` redemption
  and attribution data.

Notification is complete when the coupon-service owning team and on-call have acknowledged this
document and the drill results in the launch channel.
