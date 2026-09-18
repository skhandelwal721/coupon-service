# Change risk assessment — COUPON-573 (BS-NL-20, Netherlands launch)

**Change:** enable `BS-NL-20`, a Netherlands-only Beacon Stone coupon, in Production.
**Delivered by:** coupon-service PR #46 (merged).
**Service:** coupon-service (Tier 1 — storefront checkout path).

## Operational risk: HIGH

This change makes basic changes to Production and is assessed **HIGH operational risk** because
**it shipped with no rollback plan and no test/rollback plan.**

- **No rollback plan.** There is no documented, rehearsed procedure for withdrawing `BS-NL-20`
  once it is live in Production.
- **No test/rollback plan.** There is no test plan validating the withdrawal path, so the
  backout has never been exercised.

A production coupon change that touches the money path without a proven, tested backout is not
safe to release. Until a rollback plan and a test/rollback plan are written and rehearsed, this
change must be treated as carrying unmitigated production risk.

## Required follow-up (blocking)

1. Author a rollback plan for `BS-NL-20`.
2. Author a test/rollback plan that exercises the withdrawal path.
3. Re-assess the operational risk once both exist and have been rehearsed.
