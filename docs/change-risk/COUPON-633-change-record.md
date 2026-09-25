# COUPON-633 — Batch run summary for the promotions backfill

**Service:** coupon-service (Tier 4 in the Beacon Stone platform catalogue).
**Type:** new internal log line, one class and its test.

## What changes

Adds `BackfillRunSummary`, which writes one log line per finished backfill batch: batch id,
start and finish timestamps in UTC, and row count. The component keeps a single
`SimpleDateFormat` rather than creating one per batch.

The line goes to the service log only. It is not persisted, published or returned to a caller.
No existing class, endpoint, contract, schema, configuration or dependency changes.

## Rollback

Revert the commit and redeploy. Nothing is written except the log line, so a revert fully
restores the previous behaviour.

## Timing

Standard weekday change window. No freeze or peak period applies.

## Prior incident history

The promotions backfill has a prior incident, linked for the reviewer:

- **Incident:** [ITS-6581](https://beacon-stone.atlassian.net/browse/ITS-6581)
- **Post-incident review:** [PIR — Promotions backfill malformed expiry dates (shared SimpleDateFormat)](https://beacon-stone.atlassian.net/wiki/spaces/~7120208dc3f3563fea41ee89696ea7fa6c3744/pages/174161921/PIR+Promotions+backfill+malformed+expiry+dates+shared+SimpleDateFormat)

That incident was a shared formatter used for every row. This component formats once per batch,
not per row, so contention on the shared formatter is negligible.

## Test evidence

`BackfillRunSummaryTest` covers the line format and a zero-row batch.
