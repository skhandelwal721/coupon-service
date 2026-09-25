# COUPON-630 — Reuse one regex matcher in `SepaAddressNormaliser`

**Service:** coupon-service (**Tier 4** in the Beacon Stone platform catalogue — no external SLA).
**Type:** performance refactor, single class. **Author:** growth-platform.

## What changes

`SepaAddressNormaliser#normalise` used `String#replaceAll`, which compiles the pattern and
allocates a new `Matcher` on every call. This change compiles the pattern once and keeps one
`Matcher` on the component, calling `reset(postcode)` per invocation.

Output is unchanged for every input in `SepaAddressNormaliserTest`. No method signature, return
type, published contract, schema, configuration or dependency changes.

## Service classification

The platform catalogue lists coupon-service as **Tier 4**, and `archetype-descriptor.yaml` and
`README.md` are updated to match it. The descriptor's `tier` field is catalogue metadata only; it
is not read by compute, scaling, alarms or deployment.

## Rollout and rollback

- **Rollout:** normal pipeline deploy, all instances. No flag — the change has no new path to gate.
- **Rollback:** revert the commit and redeploy, about 5 minutes. The component is stateless across
  restarts and writes nothing, so a revert fully restores the previous behaviour.
- **Timing:** within the standard weekday change window. No freeze or peak period applies.
- **Downtime:** none. Rolling deploy.

## Prior incident history

This component family has a prior incident, linked for the reviewer:

- **Incident:** [ITS-6581](https://beacon-stone.atlassian.net/browse/ITS-6581)
- **Post-incident review:** [PIR — Promotions backfill malformed expiry dates (shared SimpleDateFormat)](https://beacon-stone.atlassian.net/wiki/spaces/~7120208dc3f3563fea41ee89696ea7fa6c3744/pages/174161921/PIR+Promotions+backfill+malformed+expiry+dates+shared+SimpleDateFormat)

That incident came from a `SimpleDateFormat` held as a field on a singleton formatter and shared
across threads. This change is different: the matcher is `reset` on every call, so each call
starts from a clean state.

## Test evidence

Existing `SepaAddressNormaliserTest` cases pass unchanged. No new tests — behaviour is identical.
