# COUPON-618 — change record

**Change:** remove the issued-identifier set from `RedemptionIdFactory`. `next()` draws one
identifier and returns it; the count is kept in an `AtomicInteger`.

---

## 1. What was wrong

COUPON-617 kept every identifier it had issued in a `HashSet` and re-drew on a repeat. The guard
addressed a case that does not arise, and introduced three that do:

| # | Problem | Why it matters |
|---|---|---|
| 1 | The set grew by one entry per redemption and nothing removed them | A long-running instance held every identifier it had ever issued. Growth is unbounded in the one dimension that only increases |
| 2 | `HashSet` is not safe for concurrent mutation, and this is a singleton written to from request threads | Concurrent `add()` can leave the backing table inconsistent |
| 3 | The re-draw loop `while (!issued.add(candidate))` had no iteration cap | Benign on its own; unbounded if (2) leaves `add()` returning false |

**They compound.** (2) is what makes (3) reachable, and (1) is what makes (2) more likely over
time as the table resizes.

## 2. The fix, and why it is the root cause

**All three problems were in the remembering, not in the drawing.** So the state is gone:

```java
public String next() {
    issued.incrementAndGet();
    return PREFIX + UUID.randomUUID();
}
```

A version 4 UUID carries 122 random bits. Two draws colliding is not a case worth holding state to
detect, and the identifier is not used as a uniqueness constraint anywhere — there is no unique
index, no upsert and no de-duplication keyed on it. Removing the set therefore removes all three
problems at once rather than patching each.

`issuedCount()` **keeps its signature and its meaning**, backed by an `AtomicInteger`: constant
memory, safe under concurrency, no iteration. No caller changes — the only call site is the test.

## 3. What this change touches, per category

Stated so each is checkable from the diff rather than taken on trust.

**Code behaviour.** Two files. One method body, one field type, one test class. `next()` returns the
same shape from the same generator, so no caller sees a different value class. No public signature
changes. `Coupon`, `CouponRepository`, `RedemptionService`, `PromotionLedger`, `AttributionExport`,
`BillingClient` and `RedemptionController` are not in this diff.

**Runtime and deployment.** No configuration, property or flag. No schema, migration or stored
structure — the factory held only in-process state and now holds less of it. No startup ordering,
no new dependency, no base-image or SDK change. The change is independently deployable in either
direction, alone, in one step.

**Interfaces and data.** No request or response shape, no published field, no event, no log line
and no stored record is added, removed or altered. Nothing crosses a service boundary: the factory
is called in-process by one caller.

**Amounts and records.** No figure is computed, derived, rounded or recorded by this change. The
only value it produces is a synthetic identifier drawn from `UUID.randomUUID()`.

## 4. Validation result

| Property | How it is shown |
|---|---|
| Identifiers remain distinct | 1,000 successive draws all distinct |
| Distinct under concurrency | 16 threads × 500 draws = 8,000 draws, all distinct |
| The count is exact under concurrency | `issuedCount()` returns exactly 8,000 after the above |
| Memory does not grow with volume | 100,000 draws, and no field on the factory is a `Collection` or `Map` |
| The prefix is unchanged | `rdm_` asserted |

## 5. Test plan

`RedemptionIdFactoryTest`, unit-level, on every build:

- `everyIdentifierCarriesThePrefix`
- `identifiersAreNotRepeated` — 1,000 draws, count exact
- `theCountTracksWhatWasIssued` — starts at zero, follows what was issued
- `concurrentDrawsAreDistinctAndCountedExactly` — the case COUPON-617's set was exposed to
- `theFactoryHoldsNoPerIdentifierState` — 100,000 draws, no collection field

Every line added by this change is executed by those five tests.

## 6. Implementation plan

1. Merge and deploy through the normal pipeline. Single step, no ordering constraint against any
   other change, no coordination with another team.
2. Confirm on the redemption dashboard that `issuedCount()` continues to report.
3. No follow-up step and no cleanup task.

## 7. Backout plan

**Procedure.** Revert the merge commit and redeploy:

```
git revert -m 1 <merge-commit>
```

**Recovery time.** One deploy. No second step, no ordering constraint, no coordination.

**What rollback restores.** COUPON-617's behaviour exactly: the issued-identifier set returns, with
the three problems in §1.

**Why the revert is a complete undo:**

- Nothing is written outside process memory, so a revert leaves no residue to correct.
- No value is stored in a new shape, so nothing exists that the reverted code could not read.
- Nothing is coupled to it — no other component needs reverting, in any order.
- Identifiers drawn under this change are indistinguishable in form from those drawn before it, so
  anything already issued stays valid either way.

**Rehearsal.** Run the test class against the reverted tree: the two new tests fail to hold and the
three original ones pass — which is COUPON-617's state, and the confirmation the revert lands where
it is expected to.

## 8. Timing

No window is required. The change is independently deployable, needs no downtime, and can go out
in normal hours in a single step. There is no freeze, embargo or peak-period constraint attached to
it.
