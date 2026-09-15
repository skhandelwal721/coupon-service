# COUPON-496 — Change record

Remediation of **COUPON-491** (PR #19, merged and live). Pull request: **#20+**.

Structured to [ECS](../../../change-risk-kb/01-northwind-engineering-change-standards.md),
[DPP](../../../change-risk-kb/02-northwind-eu-data-protection-and-regulatory-protocol.md) and
[BCT](../../../change-risk-kb/03-northwind-service-tiering-and-continuity.md). Rule identifiers
are quoted so each requirement can be checked against what is provided.

---

## 1. Classification

| Item | Value | Basis |
| --- | --- | --- |
| Service | `coupon-service` | — |
| Tier | **1** — payments path | BCT-1.1 |
| Change type | **Remediation** of an active production defect | BCT-9 |
| Defect remediated | COUPON-491 (merged, live) | — |
| Contract change | 3.1.0 → **3.2.0**, additive, wire-compatible with 2.4.0 | ECS-3.1 |
| Consumer migration required | **None** | ECS-3.6 |
| New personal data introduced | **None.** This change *removes* personal data from logs, analytics and process memory | DPP-1.2 |

---

## 2. What was wrong, and what this does about it

### Technical

| Finding from the PCDEM-53 assessment | Fix |
| --- | --- |
| `VelocityGuard:50-54` — full PAN, IP and email logged at `INFO` on **every** attempt (DPP-3.1, DPP-7.1, PCI-DSS v4.0 Req 3.3/3.4) | Nothing personal is logged at any level. Every check logs a 12-character `deviceRef` derived from a keyed HMAC — enough to correlate two attempts in one investigation, not enough to identify anyone. |
| `VelocityGuard:36-43` — unbounded `ConcurrentHashMap`s, no eviction or TTL (ECS-2.2, DPP-5.1) | Counters move to the **shared Redis cluster** — `RedisVelocityCounterStore`, which is what ECS-2.2 requires. Fixed-window `INCR` with the TTL set on the creating write, so Redis enforces the retention period itself. The in-memory store is retained for local development only and is selected only by `fraud.velocity.store: memory`. |
| Rotating the HMAC key reset every counter — a **fraud bypass on a schedule** (DPP-11.6) | Rotation is overlapped: `previousHashKey` is set for one counter window, both digests are computed, and the guard takes the higher count. An attacker at their limit carries it across a rotation. Erasure covers both digests. |
| No key validation (DPP-11.3, DPP-11.5) | Minimum 32 bytes, enforced **at startup**. A missing or short key refuses to start rather than degrading to keying on raw values. |
| `RedemptionAnalyticsClient:39-47` — string-concatenated JSON over unvalidated input | Built through Jackson. Covered by escaping tests for `"` and `\`. |
| `DeviceFingerprint:33-38` — full PAN used as a map key (DPP-7.1) | Every component is a **keyed HMAC-SHA256**. The reason the raw PAN was used — the last four collides at our volume and a collision refuses a real customer — is satisfied by hashing rather than truncating: as unique as the PAN, and not the PAN. Key comes from the platform secret store. |

### Data migration and compatibility

| Finding | Fix |
| --- | --- |
| `@NotBlank` on `deviceId` and `customerIp` → **HTTP 400 for `order-service`** pinned to 2.4.0 (ECS-3.2, ECS-3.3, ECS-3.6) | Both optional again; contract published as 3.2.0. **The security intent is preserved a different way:** an unattributed attempt is held to `maxUnattributed` (default 2) — *tighter* than the attributed limit of 3 — so omitting the fields tightens the check rather than disabling it. |

### Performance and scalability

| Finding | Fix |
| --- | --- |
| Unbounded memory growth → `OutOfMemoryError` at 593,000 redemptions/month (ECS-2.2, BCT-2) | Counters are no longer in process memory at all. Both stores **fail closed** — an unreachable cluster or an exhausted local store refuses the redemption rather than letting it through unchecked, surfacing as `503`. Alarmed by `VelocityCounterStoreUtilisation` on the cluster's own keyspace metrics at 80%. |
| Analytics HTTP call has no request timeout → thread starvation (ECS-4.4) | Both timeouts set (`connectTimeoutMillis`, `requestTimeoutMillis`, 500ms each). `publish` is also now **non-fatal** — it ran after the charge had settled, so a failure used to fail a checkout for an order already charged. |

### Operational

| Finding | Fix |
| --- | --- |
| `application.yml` raises `com.northwind.coupon.fraud` to `DEBUG` in production while emitting C3/C4 (DPP-3.4) | Package override removed. The attempt context financial crime asked for is available at `INFO` via `deviceRef`, without personal data. |

### Compliance

| Finding | Fix |
| --- | --- |
| Plaintext PAN logging (DPP-3.1, DPP-7.1, PCI 3.3/3.4) | Removed — see Technical. |
| Full PAN in in-memory map keys (DPP-7.1) | Removed — keyed HMAC. |
| C3 data logged and transmitted to third-party analytics with no DPA/TIA/DPO approval (DPP-1.1, DPP-4.2, GDPR Art. 4(1)/5(1)(c)) | Analytics payload restricted to **C1 and C2 only**. Growth's abuse dashboard segments on `deviceReference`, a non-reversible grouping label. |
| Indefinite in-memory retention, no eviction or erasure (DPP-5.1, DPP-5.2, GDPR Art. 5(1)(e)/17) | TTL-bounded, and `VelocityGuard.forget(request)` erases one subject's counters on request. |

---

## 3. What this change does **not** fix, and who owns it

Stating this is mandatory under ECS-6.2, and it is the honest part of a remediation: COUPON-491
ran in production, so some of its effects are already outside our control.

| Already happened | Why a deploy does not fix it | Owner |
| --- | --- | --- |
| **Plaintext PANs written to central log aggregation** — replicated out of region, 400-day retention | Log data is immutable once shipped. Needs a targeted purge request to the logging platform and a PCI incident record. | Security + Platform Logging |
| **C3 personal data transmitted to the third-party analytics platform** | The disclosure has occurred (DPP-10.2). Deletion does not undo it. Needs an erasure request to the processor and a DPO assessment of whether Art. 33 notification is triggered. | DPO + Legal |
| **Unevicted in-memory counters in running instances** | Cleared by the rolling deploy this change ships in. No separate action, but it is the deploy that clears them, not the code. | — |
| **Discounted checkouts that 400'd while 3.1.0 was live** | Those customers did not complete their orders. Recoverable revenue, not recoverable automatically. | Trading + Support |

Each of these needs a linked task before deployment. **A remediation PR that implies these are
fixed by merging it would be misleading** — they are not.

---

## 4. Test plan (ECS-6.1, ECS-6.6)

### Automated, committed in this PR

| Test | Asserts | COUPON-491 defect it guards |
| --- | --- | --- |
| `DeviceFingerprintTest.theKeyContainsNoCardholderData` | no PAN, not even the last four, in a velocity key | full PAN as a map key |
| `DeviceFingerprintTest.theKeyContainsNoPersonalData` / `theDeviceKeyContainsNoPersonalData` | no IP or device identifier in a key | C3 in process memory |
| `DeviceFingerprintTest.theLogSafeReferenceIsShortAndNotTheDeviceId` | the log label is 12 chars and is not the device id | PII in logs |
| `DeviceFingerprintTest.aDifferentKeyProducesADifferentDigestForTheSameInput` | keyed, not a bare digest | a plain SHA-256 of a PAN is brute-forceable |
| `DeviceFingerprintTest.aDifferentCardOnTheSameDeviceIsADifferentAttemptKey` | the fraud capability survives hashing | — |
| `InMemoryVelocityCounterStoreTest.aCounterExpiresAfterItsWindow` + `sweepingRemovesExpiredKeys…` | TTL eviction | infinite retention |
| `InMemoryVelocityCounterStoreTest.refusesRatherThanEvictingALiveCounterAtTheCeiling` | fails closed, and rolls the refused key back | unbounded growth |
| `InMemoryVelocityCounterStoreTest.aSingleSubjectsCountersCanBeErasedOnRequest` | Art. 17 erasability | no erasure path |
| `RedemptionAnalyticsClientTest.carriesNoPersonalData` / `carriesNoCardholderData` | C1/C2 only | third-party C3 transfer |
| `RedemptionAnalyticsClientTest.escapesAValueThatWouldHaveBrokenTheJson` | Jackson, not concatenation | JSON injection |
| `RedemptionAnalyticsClientTest.reportsFailureRatherThanThrowing…` | non-fatal, bounded | checkout failure after a settled charge |
| `RedisVelocityCounterStoreTest.setsTheTtlOnTheWriteThatCreatesTheKey` | fixed window, not sliding | a steady attacker never expiring |
| `RedisVelocityCounterStoreTest.refusesTheRedemptionWhenTheClusterIsUnreachable` | **fails closed** | a fraud check silently passing when its store is down |
| `RedisVelocityCounterStoreTest.peekReadsTheCountWithoutRecordingAnAttempt` | rotation read is side-effect free | double-counting across a rotation |
| `DeviceFingerprintTest.refusesToStartWithoutAKey` / `refusesAKeyShorterThanTheMinimum` | DPP-11.3, DPP-11.5 | silent fallback to raw values |
| `DeviceFingerprintTest.duringARotationBothDigestsAreReturnedAndTheyDiffer` | DPP-11.6 overlap | rotation as a fraud bypass |
| **`RedemptionRequestCompatibilityTest.aRequestFromAConsumerPinnedTo240IsValid`** | **a 2.4.0-shaped request passes validation** | **the `order-service` 400** |
| `VelocityGuardTest.acceptsARequestFromAConsumerThatSendsNoDeviceOrOrigin` | accepted, not rejected | same |
| `VelocityGuardTest.holdsAnUnattributedAttemptToAStricterLimit` | absence tightens, not disables | the reason optional is safe |

### Cross-service verification (ECS-6.6 — against the real consumer, not a mock)

| Direction | Check | Expected |
| --- | --- | --- |
| Upstream `order-service` | place a discounted order with the release candidate | **HTTP 201**, not 400. No change to `order-service` deployed or required. |
| Upstream `order-service` | confirm `pom.xml` still pins `coupon.contract.version 2.4.0` | pinned and correct — 3.2.0 is wire-compatible |
| Downstream `billing-service` | charge still succeeds; no change to the charge request shape | unchanged by this PR |
| Log estate | grep the release candidate's output for a 16-digit sequence and for `customerIp=` | no matches at any log level |
| Redis | confirm `coupon:velocity:*` keys appear with a TTL, and that counters are shared across two instances | keys present with TTL ≈ 1440 min; a second instance sees the first's count |
| Redis | stop the cluster and attempt a redemption | **503**, not a successful unchecked redemption |
| Analytics | capture one outbound payload | no `customerIp`, `deviceId`, `customerEmail` or `cardLastFour` keys |

---

## 5. Backout plan (ECS-6.1, ECS-6.2, ECS-6.4)

**How:** revert the merge commit and redeploy through the same progressive pipeline.

**Time to roll back: under 15 minutes** — within the Tier-1 maximum acceptable recovery time
(ECS-6.4, BCT-8). No schema change, no data migration, no consumer coordination.

**Rehearsed (ECS-6.3):** revert-and-redeploy rehearsed in staging against the release candidate.

### What a rollback does NOT recover

| Already happened | What a revert does |
| --- | --- |
| PANs in log aggregation | Nothing. Immutable once shipped, 400-day retention. Needs a purge request. |
| C3 data at the analytics processor | Nothing. The disclosure has occurred (DPP-10.2). |
| Discounted checkouts that 400'd under 3.1.0 | Nothing. Those orders were not placed. |

**Reverting reinstates the defect.** The rollback target is COUPON-491's state, which is a
known-defective live state: plaintext cardholder data in logs, an unbounded heap, and a 400 on
every discounted checkout. **Fix-forward is the preferred direction** unless this change itself
causes a new failure.

---

## 6. Deployment plan (ECS-5.1, ECS-6.5)

**Window: 01:00–04:00 UTC, Tuesday–Thursday** — the standard maintenance window. Avoids every
prohibited window in ECS-5.2: SEPA settlement cut-off 15:00–17:00 CET, European evening retail
peak 17:00–22:00 CET, weekend peak Fri 16:00 – Mon 06:00 CET, quarter-end close, peak trading
embargo. **No freeze exception required**, so no ECS-5.3 breach — unlike COUPON-491, which the
assessment found was deployed outside an approved window with no CAB record.

**Pre-deployment, both mandatory:**

1. `FRAUD_VELOCITY_HASH_KEY` exists in the target secret store and is at least 32 bytes. The
   service will not start otherwise, deliberately — a missing or weak key must not degrade to
   keying on raw values (DPP-11.3, DPP-11.5).
2. `REDIS_URL` points at a reachable `redis-velocity-counters` cluster. The guard fails closed,
   so an unreachable cluster refuses redemptions — this is a **new hard dependency on the
   checkout path** and must be confirmed before the first canary step, not during it.

**Rollout:** 5% → 30-min soak → 25% → 30-min soak → 100%. ~70 minutes.

**Abort on:** `VelocityCounterStoreUtilisation` firing · `VelocityRefusalRate` outside its
normal band in either direction · any 16-digit sequence appearing in log output ·
`order-service` 4xx rate on `POST /v1/redemptions` above zero · any `503` from the redemption
endpoint, which means the counter store is not answering.

**On-call (ECS-5.4):** `coupon-service` on-call and SME on shift for the window.
`order-service` on-call on notice, since their 400s are what this fixes.

---

## 7. Approvals required (ECS-10, DPP-8)

Each must be recorded on the ticket before it leaves Review (ECS-10.1). No self-approval
(ECS-10.2).

| Approver | Why | Rule |
| --- | --- | --- |
| Service owner + lead engineer | any Tier-1 change | ECS-10 |
| **DPO** | changes what personal data is logged, transmitted and retained — in the reducing direction, but it is still a change of processing | DPP-8, DPP-8.1 |
| **Security / CISO** | changes a masking and hashing mechanism | DPP-3.3, DPP-7.3, ECS-2.3 |
| **Financial Crime** | changes fraud and velocity control behaviour, including the new unattributed limit | DPP-8, ECS-2.5 |
| **Architecture Review Board** | published contract version bump, **and** a new hard runtime dependency on the checkout path (`redis-velocity-counters`) | ECS-10, ECS-3.6, DPP-9.5 |
| **CAB** | Tier-1 payments path | ECS-10, BCT-1.1 |

**Not required:** Finance Controller. No monetary field, rounding, scale or basis changes here.

---

## 8. Cryptography compliance (DPP-11)

The pseudonymisation this change introduces is mapped to the standard rather than left as a
judgement call:

| Rule | Requirement | This change |
| --- | --- | --- |
| DPP-11.1 | HMAC-SHA256 with a secret key, Base64url output | ✅ `DeviceFingerprint` |
| DPP-11.2 | Keyed, because a bare digest of a 16-digit PAN is reversible by exhaustive search | ✅ keyed HMAC, asserted by `aDifferentKeyProducesADifferentDigestForTheSameInput` |
| DPP-11.3 | Minimum 32-byte key, rejected at startup | ✅ `MINIMUM_KEY_BYTES`, asserted by `refusesAKeyShorterThanTheMinimum` |
| DPP-11.4 | Key from the platform secret store, never logged, never leaves the process | ✅ `secret://coupon-service/fraud-velocity-hash-key`; the key is a private field and appears in no log statement |
| DPP-11.5 | Fail closed on a missing key — no fallback to the raw value | ✅ asserted by `refusesToStartWithoutAKey` |
| DPP-11.6 | Rotation overlapped by one state window; the control takes the more conservative value; erasure covers both digests | ✅ `previousHashKey`, `Keys.hasPrevious()`, `VelocityGuard.countFor` takes the max; `forget` covers both |
| DPP-11.7 | Pseudonymised data is still personal data, subject to DPP-5 in full | ✅ TTL retention and per-subject erasure both implemented |
| DPP-11.8 | A digest prefix may be logged, ≤ 16 characters | ✅ 12 characters, asserted by `theLogSafeReferenceIsShortAndNotTheDeviceId` |

**No construction outside DPP-11.1 is introduced.** The Security sign-off in §7 is still
required (DPP-11.9); what is not required is treating this as an unassessed cryptographic risk.

### ECS-2.2 — now satisfied

The earlier draft of this change kept counters in process memory and disclosed the gap. That is
closed: `RedisVelocityCounterStore` is the default and the only production store, so counters
are shared across instances. The two consequences that made the in-memory version unacceptable
are both gone — an attacker spread across 2–12 instances no longer gets *n* times the limit, and
a rolling deploy no longer resets every counter.

**COUPON-497 is closed by this change** rather than deferred.

New dependency: `redis-velocity-counters`, declared hard in `archetype-descriptor.yaml`. The
guard fails closed, so an unreachable cluster refuses redemptions — that is the correct direction
for a fraud control and it is a new availability dependency on the checkout path, which the
Architecture Review Board should see explicitly.

## 9. Business case (BCT-3, BCT-9)

| Measure | Value |
| --- | --- |
| Cost of **not** deploying — discounted checkout failing closed | **£205,000/hour**, £4.92m/day deferred (BCT-2.2, BCT-3) |
| Cost of **not** deploying — regulatory | GDPR Art. 83 ceiling €20m or 4% of turnover; PCI cardholder-data exposure and card-scheme penalties (DPP-10.3, BCT-7.2, BCT-4.2) |
| Cost of **not** deploying — disclosure clocks | GDPR Art. 33 72-hour and DORA Art. 19 4-hour notifications already potentially running from COUPON-491 (BCT-4.4) |
| Cost of deploying | one 70-minute progressive rollout inside the maintenance window |
| Customer impact of deploying | none expected — this restores checkout |
| Customer impact of **not** deploying | **CI-2** (cannot complete a purchase) plus **CI-1** (cardholder data exposed) — BCT-6 |

**BCT-9.2 assessment:** defect live and costed ✅ · no new capability ✅ · no consumer migration
✅ · rollback under tier RTO and rehearsed ✅ · tests and alarms committed ✅ · approved window
with progressive rollout ✅. Qualifies as a remediation, so business risk is scored on the delta.
Per BCT-9.3, the ECS-10 approvals above are unchanged and still required.
