# COUPON-612 — rollback plan and test plan

**Change:** add a guard to `Coupon#discountMinorUnitsFor` so a `null` or non-positive argument
answers zero instead of throwing or returning a negative figure, and add `hasFixedAmount()` so a
caller can ask whether `discountMinorUnits()` will answer.

## What the change touches

One method body and one new predicate, both in `Coupon.java`, plus one test class. The change is
confined to those three files: it adds two branches inside a method that already existed, and a
predicate that reads one field it already held. It writes no state, introduces no call path, and
alters no shape any caller can observe beyond the two argument cases named above.

## Rollback plan

**Procedure.** Revert the merge commit and redeploy:

```
git revert -m 1 <merge-commit>
```

Then deploy the reverted build through the normal pipeline.

**Recovery time.** One deploy. There is no second step, no ordering constraint against another
change, and nothing to coordinate with another team.

**What rollback restores.** The prior behaviour of `discountMinorUnitsFor` exactly: a `null`
argument throws `NullPointerException` again, a negative argument returns a negative figure again,
and `hasFixedAmount()` no longer exists. Nothing else changes.

**Why the rollback is safe rather than merely available:**

- **Nothing to unwind.** The change writes no state, so a revert leaves no residue to correct. A
  revert is a complete undo, not a partial one.
- **No data written in the new shape.** No field, record or artefact is produced by this change, so
  there is no value stored under the new behaviour that the old code could not read.
- **No coupled deployment.** No other component needs to be reverted with it, in any order.
- **Reverting cannot be worse than not deploying.** The guard only replaces two failing inputs
  with zero; reverting restores the failures, which is the state the service is in today.

**Rehearsal.** The revert is exercised by running the test class below against the reverted tree:
the four guard tests fail and the arithmetic tests pass, which is exactly the prior behaviour. That
is the confirmation the revert lands where it is expected to.

## Test plan

`CouponSubtotalGuardTest`, all unit-level and run on every build:

| Test | Confirms |
|---|---|
| `aNullSubtotalAnswersZero` | the `NullPointerException` path is gone |
| `aNegativeSubtotalAnswersZero` | `-1` and `-24900` answer zero rather than a negative figure |
| `aZeroSubtotalAnswersZero` | zero in, zero out |
| `theGuardedAnswerHasTheSameScaleAsTheComputedAnswer` | both answers are scale 0, so callers see one shape |
| `positiveSubtotalsAreUnaffected` | `24900 → 4980`, `10000 → 2000`, `1001 → 200`, `9 → 1` are unchanged |
| `fixedCouponsAreUnaffectedByTheGuard` | a FIXED coupon returns its own amount for `null`, negative and positive arguments alike |
| `callersCanAskWhetherDiscountMinorUnitsWillAnswer` | the predicate agrees with the method it describes |

`CouponPercentageTest`, `CouponTest`, `CouponCountryRestrictionTest` and
`NetworkPromotionRulesTest` continue to cover the behaviour this change does not alter, and are run
unchanged.

**Coverage of the change itself.** Every line added by this change is executed by the tests above:
the two guard branches, the predicate in both its states, and the arithmetic path the guard sits in
front of.
