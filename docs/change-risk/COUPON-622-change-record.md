# COUPON-622 — change record

The records this change is obliged to carry, in one place, so each can be checked rather than taken
on trust.

**Change:** stop `discountMinorUnits()` answering zero for an entry with no amount recorded, and
check the invariant where entries are published instead.

---

## 1. Declared scope — everything shipped

| File | What changes |
|---|---|
| `src/main/java/com/beaconstone/coupon/promotion/Coupon.java` | COUPON-620's silent-zero branch removed; `isWellFormed()` added |
| `src/main/java/com/beaconstone/coupon/promotion/CouponRepository.java` | `publishable(...)` gate called before the catalogue is published |
| `src/test/java/com/beaconstone/coupon/promotion/CouponAbsentAmountTest.java` | Rewritten for the gate and the restored accessor |
| `docs/change-risk/COUPON-622-change-record.md` | This record |

Nothing else. No configuration, property or flag; no schema, migration or stored state; no request
or response shape; no dependency; no infrastructure, pipeline or manifest file.

## 2. What was wrong, at the root

COUPON-620 removed a `NullPointerException` by having the read site answer **zero**. Two things were
wrong, and only the second is the root cause:

- **The symptom it created.** Zero is a legitimate amount, so the answer was indistinguishable from
  a real one. A malformed entry read as "nothing off", and the exception that would have exposed it
  was removed in the same change — the defect and its own detection went together.
- **The root cause it left in place.** The catalogue could publish an entry that did not satisfy its
  own invariant, and nothing checked. A published entry is resolvable, and a resolvable entry gets
  acted on. Patching the read site could never fix that; it only decided what the wrong answer would
  be.

**So the check belongs at publication.** `CouponRepository.publishable(...)` runs over every entry
as the catalogue is assembled and refuses the lot if any fails, naming the codes that did. The
catalogue is assembled once at startup, so the fault surfaces on deploy.

**Why a predicate and not a constructor check.** `Coupon.isWellFormed()` makes the invariant
askable. Construction stays permissive so a test or a migration can build a partial entry
deliberately; publication is what must be strict. Being unable to ask is what left COUPON-620 with
nothing to do but answer anyway.

## 3. Validation result

The real catalogue, every entry, both flag states — asserted by
`everyEntryInTheRealCatalogueIsWellFormed`:

| Flag state | Entries published | Well formed |
|---|---|---|
| `promotions.nlLaunch.enabled=false` | **7** — `NW-VISA-10`, `NW-SUMMER-25`, `NW-MC-15`, `NW-SEPA-10`, `NW-SEPA-25`, `NW-SEPA-15`, `BS-EU-20` | 7 of 7 |
| `promotions.nlLaunch.enabled=true` | **8** — the above plus `BS-NL-20` | 8 of 8 |

So the gate added here **refuses nothing that exists today**: it catches faults, not entries. That is
the figure this record is asked for, and it is checkable by running the test.

Amounts are unchanged, asserted value by value: `10.00 → 1000`, `24.90 → 2490`, `0 → 0`, and
`10.099 → 1009` for the truncation.

## 4. Test plan

`CouponAbsentAmountTest`, unit-level, on every build:

| Test | Confirms |
|---|---|
| `anEntryKnowsWhetherItIsWellFormed` | the invariant is askable; a real zero is well formed |
| `aMalformedEntryCannotBePublished` | publication refuses, and the failure names the entry |
| `aWellFormedCatalogueIsPublishedUnchanged` | the gate returns the same map, adding no copy or reorder |
| `everyMalformedEntryIsNamed` | all failing codes are listed, and well-formed ones are not |
| `theAccessorAnswersForAWellFormedEntryOnly` | one input class, one answer; truncation unchanged |
| `everyEntryInTheRealCatalogueIsWellFormed` | the figure in §3 |

Every line added by this change is executed by those tests.

## 5. Rollback plan

**Procedure.** Revert the merge commit and redeploy:

```
git revert -m 1 <merge-commit>
```

**Recovery time.** One deploy. No second step, no ordering constraint against another change, no
coordination with another team.

**What rollback restores.** COUPON-620's behaviour exactly: an absent amount answers zero again, and
`isWellFormed()` and the publication gate no longer exist. Nothing else changes.

**Why the revert is a complete undo, not a partial one:**

- **Nothing to unwind.** The change writes no state, so a revert leaves no residue to correct.
- **No value stored in a new shape.** No field, record or artefact is produced, so there is nothing
  stored under the new behaviour that the old code could not read.
- **No coupled deployment.** Nothing else must be reverted with it, in any order.
- **Reverting is availability-neutral.** The gate passes for all 7 or 8 published entries (§3), so it
  is inert on the catalogue as it stands; removing it changes nothing currently exercised.

**Rehearsal.** Run the test class against the reverted tree: the four gate tests fail to compile
against the removed methods and the accessor tests pass — exactly COUPON-620's state, and the
confirmation the revert lands where expected.

## 6. Operational profile

- **No downtime.** A code-only change; no sequencing beyond a normal rolling deploy.
- **No maintenance window.** Nothing is taken out of service and no traffic is paused.
- **No infrastructure touched.** No manifest, pipeline, capacity, network or routing change.
- **No coordination.** No other service or team needs to act before, during or after.
- **No consumer notification.** No published field, contract or shape changes.

## 7. What these records close

| Obligation | Artefact that satisfies it | Where |
|---|---|---|
| A test plan for the change | `CouponAbsentAmountTest`, six named tests | §4 |
| A backout plan for the change | Revert procedure, recovery time, completeness argument, rehearsal | §5 |
| A stated validation result | 7 of 7 and 8 of 8 entries well formed, per flag state | §3 |
| Declaration of everything shipped | Four files, exhaustive | §1 |
| The defect COUPON-620 introduced | Removed at the read site and checked at publication | §2 |

## 8. Precedent

- [ITS-6417](https://beacon-stone.atlassian.net/browse/ITS-6417) (SEV-3, resolved)
- [ITS-6417 post-incident review](https://beacon-stone.atlassian.net/wiki/spaces/~7120208dc3f3563fea41ee89696ea7fa6c3744/pages/151519389)

That review's standing action is that a caller be able to ask before it calls. `isWellFormed()` is
that ability; the publication gate is the caller that uses it. **History, not an obligation** —
recorded so the precedent for this approach is visible.
