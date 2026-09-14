# COUPON-495 — Change record

Remediation of COUPON-490. Linked ticket: **PCDEM-46**. Pull request: **#22**.

Structured to the requirements in
[Northwind Engineering Change Standards](../../../change-risk-kb/01-northwind-engineering-change-standards.md)
(ECS) and
[Service Tiering, Business Continuity & Customer Impact](../../../change-risk-kb/03-northwind-service-tiering-and-continuity.md)
(BCT). Rule identifiers are quoted so each requirement can be checked against what is provided.

---

## 1. Classification

| Item | Value | Basis |
| --- | --- | --- |
| Service | `coupon-service` | — |
| Tier | **1** — payments path | BCT-1.1 |
| Change type | **Remediation** of an active production defect | — |
| Defect being remediated | COUPON-490 (merged, live) | — |
| Blast radius | 3 services: `coupon-service`, `order-service`, `billing-service` | BCT-1.2 |
| Contract change | `docs/api/redemption.md` → **3.1.0**, additive, wire-compatible with 2.4.0 | ECS-3.1 |
| Consumer migration required | **None.** 3.1.0 restores the meaning 2.4.0 published | ECS-3.6 |

**Not a new feature.** Every change here removes risk that is live in production right now. The
comparison that matters is deploying versus not deploying, not deploying versus the state before
COUPON-490.

---

## 2. Test plan (ECS-6.1, ECS-6.6)

### Automated — committed in this PR, running in CI

| Test | Asserts | Which COUPON-490 defect it would have caught |
| --- | --- | --- |
| `RedemptionReceiptContractTest.discountIsPublishedInMajorUnits` | `discount` is published in major units | **The root cause.** A future reinterpretation of the field fails here. |
| `RedemptionReceiptContractTest.minorUnitsArePublishedInTheirOwnField` | the SEPA representation has its own name | that a new representation does not overwrite an existing field |
| `RedemptionReceiptContractTest.theTwoRepresentationsAgree` + `agreementHoldsForEveryCatalogueAmount` | both fields derive from one figure, on every catalogue amount | silent drift between the two |
| `RedemptionReceiptContractTest.aConsumerPinnedToTheOldContractStillReadsTheRightNumber` | a 2.4.0 consumer reads the correct magnitude | the upstream `order-service` free-order defect |
| `RedemptionReceiptContractTest.theNewFieldsAreAdditive` | no field removed or renamed | an accidental breaking change |
| `PromotionLedgerTest.booksMajorUnitsNotMinorUnits` | the ledger books 24.90, not 2490 | the 100× overstated network liability |
| `PromotionLedgerTest.booksTheSameBasisRegardlessOfSettlementCurrency` | EUR books on the same basis as GBP | a per-currency basis divergence |
| `AttributionExportTest.exportsAnOrdinaryPromotionRatherThanHoldingIt` | an ordinary promotion is exported, not held | the finance export silently dropping the largest promotions |
| `AttributionExportTest.theCeilingIsExpressedInMajorUnits` | states what the ceiling does to minor-unit figures | — (documents the failure mode) |
| `BillingClientTest.sendsThePostcodeExactlyAsTheStorefrontCollectedIt` | the charge carries `DE-10115`, not `DE10115` | the downstream VAT misdeclaration |
| `BillingClientTest.doesNotSendTheSepaNormalisedForm` | the two forms are not interchangeable | a future re-introduction of the same bug |
| `SepaAddressNormaliserTest.theNormalisedFormLosesThePrefixBillingMatchesOn` | why the boundary exists | — (documents the boundary) |

**Result: 49 tests, 0 failures.** Run on every push and pull request by
`.github/workflows/build.yml`.

### Cross-service verification (ECS-6.6 — tested against the actual consumer, not a mock)

| Direction | Check | Expected |
| --- | --- | --- |
| Upstream `order-service` | Place a discounted order on the EUR catalogue with the release candidate. Read `OrderTotalCalculator` output. | Payable = `subtotal − 24.90`, **not** zero. No change to `order-service` required or deployed. |
| Upstream `order-service` | Confirm `pom.xml` still pins `coupon.contract.version 2.4.0` and no migration ticket is open. | Pinned at 2.4.0 and correct — 3.1.0 is wire-compatible. |
| Downstream `billing-service` | Charge with `billingPostcode = "DE-10115"`. Read the `jurisdiction` parameter sent to tax-service. | `DE`, not `GB`. `PlaceOfSupply.isResolved` true, no `"no billingPostcode"` or `"unresolved VAT jurisdiction"` warning. |
| Downstream `billing-service` | Charge with `billingPostcode = "EC2A 4BX"`. | `GB`, resolved rather than defaulted. |
| Same repo | `PromotionLedger.liabilityFor(VISA)` after one `NW-SEPA-25` redemption. | `25.00`. Not `2500`. |
| Same repo | `AttributionExport.build(...)` for a day of ordinary promotions. | All rows exported, `exceptions` empty. |

### Reconciliation of the exposure window (BCT-5.3)

Separate from this deployment, and **not** gated on it: identify every redemption booked while
COUPON-490 was live, restate the affected ledger rows, and re-issue the affected attribution
exports. Owner: Finance Operations. Tracked separately — a fix does not restate history.

---

## 3. Backout plan (ECS-6.1, ECS-6.2, ECS-6.4)

### How to roll back

1. Revert the merge commit for PR #22 on `main`.
2. Deploy through the same progressive pipeline (§4), or use the emergency single-step path if
   the failure is customer-visible.
3. Re-pin `docs/api/redemption.md` to 3.0.0 and notify consumers that 3.1.0 is withdrawn.

**Time to roll back: under 15 minutes** — within the Tier-1 maximum acceptable recovery time
(ECS-6.4, BCT-8). No schema change, no data migration, no stateful component, no consumer
coordination.

### What a rollback restores

Full behavioural revert. This change adds a field and restores the basis of another; reverting
returns both to the COUPON-490 state.

### What a rollback does NOT recover (ECS-6.2 — mandatory to state)

| Already happened | What a revert does |
| --- | --- |
| Ledger rows booked at 100× while COUPON-490 was live | **Nothing.** The rows are written at the figure that was current. Requires manual restatement. |
| Attribution exports already sent to finance | **Nothing.** Already reconciled against. Requires re-issue. |
| VAT declared at the UK rate on euro supplies | **Nothing.** Requires a correction filing in each affected member state. |
| Charges already taken | **Nothing.** Irreversible once settled (BCT-5.1). |
| Orders released free to customers | **Nothing.** Goods dispatched. |

**Rolling back this PR reinstates the defect.** Backing out is therefore worse than rolling
forward unless this change itself is causing a new failure. If it is, the rollback target is
COUPON-490's state — which is a known-defective state, so a fix-forward is preferred and
`ResidencyZone`-style feature gating is not available here.

### Rehearsed rollback (ECS-6.3)

Revert-and-redeploy rehearsed in staging against the release candidate. Evidence: CI run on the
revert commit, linked from PCDEM-46. To be re-run within 30 days of deployment if the deploy
date slips.

---

## 4. Deployment plan (ECS-5.1, ECS-6.5)

### Window (ECS-5.1)

**01:00–04:00 UTC, Tuesday–Thursday** — the standard maintenance window.

Explicitly avoids every prohibited window in ECS-5.2:

| Prohibited window | Avoided |
| --- | --- |
| SEPA settlement cut-off, 15:00–17:00 CET | ✅ deploying 02:00–05:00 CET |
| European evening retail peak, 17:00–22:00 CET | ✅ |
| Weekend retail peak, Fri 16:00 – Mon 06:00 CET | ✅ Tue–Thu only |
| Quarter-end financial close, final 3 business days | ✅ not in the window |
| Peak trading embargo, Black Friday week and 1–24 Dec | ✅ not in the window |

No freeze exception required, so no CAB exception under ECS-5.3.

### Progressive rollout (ECS-6.5)

5% → 30-minute soak → 25% → 30-minute soak → 100%. Automated abort on error-rate or latency
regression. Total ~70 minutes, inside the window.

### Abort criteria

Abort and hold at the current percentage on any of:

- `RedemptionHeldRate` above threshold
- `BillingChargeDeserializationFailures` non-zero
- `PromotionLiabilityPerRedemptionOutOfBand` firing (added by this PR)
- any `PlaceOfSupply` `"unresolved VAT jurisdiction"` warning on a request carrying a postcode
- `order-service` `OrdersStuckUnreleased` rising

### On-call and SME (ECS-5.4)

Named `coupon-service` on-call and the service SME on shift for the full window, in a timezone
overlapping it. `billing-service` on-call on notice, as the VAT path changes. To be recorded on
PCDEM-46 before approval.

---

## 5. Observability added (ECS-9.1)

COUPON-490's defects were all silent. ECS-9.1 requires every identified silent-failure mode to
have an alarm before the change ships.

| Silent failure mode | Control added |
| --- | --- |
| A money field's basis changes undetected | `PromotionLiabilityPerRedemptionOutOfBand` (P1), added to `archetype-descriptor.yaml` by this PR. Liability per redemption moves by orders of magnitude on a basis change. |
| The postcode form silently changes the VAT jurisdiction | `BillingClientTest.doesNotSendTheSepaNormalisedForm` — build-time rather than runtime, which is stronger |
| The contract basis drifts from what is published | `RedemptionReceiptContractTest` — build-time |

---

## 6. Approvals required (ECS-10, DPP-8)

These are **not** discretionary for this change. Each must be recorded on PCDEM-46 before it
leaves Review (ECS-10.1). Self-approval is not permitted (ECS-10.2).

| Approver | Why required | Rule |
| --- | --- | --- |
| **Service owner + lead engineer** | any change to a Tier-1 service | ECS-10 |
| **Architecture Review Board** | published contract version bump (3.0.0 → 3.1.0) | ECS-10, ECS-3.6 |
| **Finance Controller** | monetary field basis restored; ledger and attribution figures affected | ECS-10, ECS-2.4 |
| **CAB** | Tier-1 payments path change | ECS-10, BCT-1.1, DPP-8 |
| **Tax & Finance** | VAT place-of-supply input changes form | DPP-2.2 |

**Not required:** DPO / Security — this change introduces no C3 or C4 field, alters no masking,
hashing or tokenisation, and changes no data location (DPP-1.2, DPP-6, DPP-7). The postcode was
already being sent before this change; only its formatting changes.

---

## 7. Business case (BCT-3)

| Measure | Value | Source |
| --- | --- | --- |
| Cost of **not** deploying — free order releases | **£4.92m/day** (19,760 discounted orders × £249.00) | BCT-2, BCT-3 |
| Cost of **not** deploying — misstated network receivable | **£8.8m/month** | BCT-3 |
| Cost of **not** deploying — VAT misdeclared | correction filing per member state, plus interest and penalty | BCT-3 |
| Cost of deploying | one 70-minute progressive rollout inside the maintenance window | §4 |
| Customer impact of deploying | none expected; this restores correct pricing | BCT-6 |
| Customer impact of **not** deploying | **CI-1 (Severe)** — customers charged incorrectly | BCT-6 |

The risk of this change is the risk of a Tier-1 deployment. The risk of *not* making it is an
active CI-1 defect at £4.92m/day. Both belong on the record.
