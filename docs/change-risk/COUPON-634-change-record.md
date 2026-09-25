# COUPON-634 — Thread-safe formatter for the backfill run summary

**Service:** coupon-service (Tier 4 in the Beacon Stone platform catalogue).
**Type:** fix to one class, plus a test. Fixes COUPON-633.

## What changes

`BackfillRunSummary` kept one `SimpleDateFormat` shared by every call. `SimpleDateFormat` is not
thread-safe, and backfill workers finish batches concurrently, so interleaved calls could log the
wrong timestamp or throw.

This replaces it with a `static final DateTimeFormatter`, which is immutable and thread-safe. The
log line is byte-for-byte the same. This is the same fix applied to `PromotionExpiryFormatter`
after [ITS-6581](https://beacon-stone.atlassian.net/browse/ITS-6581).

No method signature, endpoint, contract, schema, configuration or dependency changes.

## Test evidence

- `BackfillRunSummaryTest`: the existing line-format tests pass unchanged.
- `BackfillRunSummaryConcurrencyTest` (new): one shared instance, 16 threads, 20,000 calls across
  6 inputs. It asserts every call returns the line for its own input and no call throws. This
  test fails against the COUPON-633 code and passes with this fix.

## Compliance

Meets the Beacon Stone EU Security and Data Integrity Rules for Software Changes (GDPR Art. 32,
GDPR Art. 25, NIS2 Art. 21, ISO/IEC 27001):

- The component no longer shares an unsafe object.
- The multi-threaded test (at least 8 threads, 10,000 calls) is in the repository.

## Rollback

Revert the commit and redeploy. Nothing is written except the log line.

## Timing

Standard weekday change window. No freeze or peak period applies.
