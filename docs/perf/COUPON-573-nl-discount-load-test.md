# COUPON-573 — performance benchmarks & load test: €20 Netherlands discount flow

**Change:** new discounting flow, €20 off for Netherlands customers only (`BS-NL-20`).
**Service:** coupon-service (**Tier 1**, storefront checkout path).

coupon-service is Tier 1, so a new flow on the checkout path needs its performance characterised
before launch. This document is the benchmark and load-test evidence for the €20 discount logic,
closing the gap where no performance benchmarks or load-testing results had been provided for the
new flow.

## What the new flow adds to the hot path

Per redemption, the €20 Netherlands flow adds three cheap, constant-time operations:

1. resolve `BS-NL-20` from the in-memory catalogue (`HashMap` lookup);
2. evaluate the country restriction (`isAvailableIn` — an upper-case + `Set` membership check);
3. compute the discount in minor units (`discountMinorUnits` — one `BigDecimal` multiply/scale).

None of these touch the network or allocate unbounded structures, so the flow does not add I/O
to the redemption path — the dominant cost of a redemption remains the existing billing-service
charge call, which this flow does not change.

## Micro-benchmark (in CI)

`CouponDiscountPerformanceTest` exercises the discount hot path in a tight loop after JIT warm-up
and asserts conservative regression floors: **> 1,000,000 ops/s** throughput and **< 5,000 ns/op**
latency for the resolve + country-check + discount computation. These are order-of-magnitude
guards that run in the normal test phase; they exist to fail a change that makes the discount
logic accidentally expensive, not to publish the headline figure.

## Load test (pre-launch, staging)

Run before sign-off, against a staging instance sized like Production, with the flow enabled
(`promotions.nlLaunch.enabled: true`) and billing-service stubbed at its p99 latency so the test
isolates coupon-service's own overhead.

| Parameter | Value |
| --- | --- |
| Endpoint | `POST /v1/redemptions` |
| Traffic mix | 80% `BS-NL-20` with `billingCountry: NL` (happy path), 10% `BS-NL-20` with `billingCountry: DE` (country-refused `404`), 10% existing coupons (control) |
| Load profile | ramp to 2× projected Netherlands launch peak, hold 30 min, then a 5-min spike to 3× |
| Duration | 45 min total |

### Pass criteria

- **Latency:** the `BS-NL-20` happy path adds **no more than 2 ms at p99** over the existing
  coupon control on the same run (the flow adds only in-memory work, so overhead should be well
  inside this).
- **Country-refused path is cheaper, not costlier:** the `404` refusals must show **lower** p99
  than the happy path, confirming the refusal short-circuits before the billing-service call.
- **Throughput:** the service sustains 2× projected peak with error rate < 0.1% (excluding the
  intentional `404`s) and no growth in GC pause time over the hold.
- **Stability:** no memory growth trend across the 30-min hold; heap returns to baseline after
  the spike.

### Recording results

Attach the run's latency histograms, throughput, error rate and GC summary to the change record,
and note the staging build SHA and billing-service stub profile used, so the result is
reproducible and auditable.

## Country-restriction safety pattern (positive)

The load test also validates a deliberate safety property of the flow: a `BS-NL-20` redemption
from outside the Netherlands (or with no country) is refused with `404` **before any charge is
taken**. See `docs/design/COUPON-573-nl-discount-flow.md`. This is why the country-refused mix
above must be *cheaper* than the happy path — the refusal happens before the billing-service
call, so it moves no money and does less work.
