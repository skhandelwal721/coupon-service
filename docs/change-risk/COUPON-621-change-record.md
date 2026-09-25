# COUPON-621 — change record

**Change:** in `SepaAddressNormaliser`, compile the strip pattern once, reuse a single matcher
across calls, and pin the upper-casing locale to `Locale.ROOT`.

---

## 1. Declared scope — everything shipped

| File | What changes |
|---|---|
| `src/main/java/com/beaconstone/coupon/sepa/SepaAddressNormaliser.java` | Pattern compiled once; matcher held and reset per call; `toUpperCase(Locale.ROOT)` |
| `src/test/java/com/beaconstone/coupon/sepa/SepaAddressNormaliserLocaleTest.java` | New |
| `docs/change-risk/COUPON-621-change-record.md` | This record |

Nothing else. No configuration, property or flag; no schema, migration or stored state; no request
or response shape; no dependency, base image or SDK version; no infrastructure, pipeline or manifest
file.

## 2. The two changes, and the one that alters behaviour

**Pattern compiled once — no behaviour change.** `String#replaceAll` recompiles its argument on
every call. `Pattern.compile(...)` held in a `static final` field produces the identical result from
the identical input; only the work done to get there differs.

**Matcher reused — no behaviour change.** `Pattern#matcher` allocates on every call, and this
method is on the hot path. One matcher is held on the component and `reset(...)` before each use,
which keeps the allocation out of the loop. `reset` returns the same matcher positioned at the start
of the new input, so each call sees the same state a fresh matcher would.

**Locale pinned — this one does alter behaviour, in one direction.** `toUpperCase()` with no
argument uses whatever locale the JVM was started with, so the same input could leave this method as
two different values on two hosts. In a Turkish default locale `"i"` upper-cases to `"İ"` — a
character outside the alphanumeric set this element accepts, and one the strip pattern has already
run past, so it would leave here unaccepted. `Locale.ROOT` makes the output a function of the input
alone.

**Who is affected by that difference:** only a caller running under a locale whose upper-casing
rules differ from the root locale, and only for an input containing `i` or `ı`. Under such a locale
the previous output was already wrong. Under any other locale the output is byte-identical before
and after.

## 3. Test plan

`SepaAddressNormaliserLocaleTest`, unit-level, on every build:

| Test | Confirms |
|---|---|
| `theAnswerDoesNotDependOnTheDefaultLocale` | the same input answers the same under `tr` and `en` defaults |
| `separatorsAndSpacesAreStripped` | `DE-10115 → DE10115`, `EC2A 4BX → EC2A4BX`, `nl 1012 ab → NL1012AB` |
| `anAlreadyNormalisedValueIsUnchanged` | an idempotent input is untouched |
| `absentAndEmptyInputsAnswerNull` | `null`, blank, and all-separator inputs answer `null` |
| `repeatedCallsAgree` | 1,000 calls through the shared compiled pattern agree with one |

Every line added by this change is executed by those tests. The default locale is restored after
each test, so the suite leaves no global state behind.

## 4. Rollback plan

**Procedure.** Revert the merge commit and redeploy:

```
git revert -m 1 <merge-commit>
```

**Recovery time.** One deploy. No second step, no ordering constraint against another change, no
coordination with another team.

**What rollback restores.** The prior behaviour exactly: the pattern recompiles per call, and
upper-casing follows the JVM default locale again.

**Why the revert is a complete undo, not a partial one:**

- **Nothing to unwind.** The method writes no state, so a revert leaves no residue to correct.
- **No value stored in a new shape.** Nothing produced by this change is persisted, so there is no
  record the old code could not read.
- **No coupled deployment.** Nothing else must be reverted with it, in any order.
- **Reverting is availability-neutral.** Under the root locale — which is what the deployed hosts
  run — output is identical either way, so removing the change exercises nothing new.

**Rehearsal.** Run the test class against the reverted tree: `theAnswerDoesNotDependOnTheDefaultLocale`
fails and the remaining four pass, which is exactly the prior state and the confirmation the revert
lands where expected.

## 5. Operational profile

- **No downtime.** A code-only change to an in-process method; no restart sequencing beyond the
  normal rolling deploy.
- **No maintenance window required.** Nothing is taken out of service and no traffic is paused.
- **No infrastructure touched.** No manifest, pipeline, capacity, network or routing change.
- **No coordination.** No other service or team needs to act before, during or after; nothing
  downstream sees a different value under the deployed locale.
- **No consumer notification.** No published field, contract or shape changes.
