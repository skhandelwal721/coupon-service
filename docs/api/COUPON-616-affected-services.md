# COUPON-616 — services affected by renaming `discount` on the wire

**Change:** the redemption receipt serialises its discount as **`discountMinorUnits`** instead of
`discount`. One annotation on `RedemptionReceipt`; the Java accessor is unchanged, so no in-process
caller in this repository is affected.

**Why it is not a small change in effect.** `docs/api/redemption.md` records that `discount` changed
meaning at 3.0.0 — major units to minor units — while keeping its name and its `BigDecimal` type. A
consumer that has not been updated therefore reads a figure **100× larger than intended**,
arithmetically valid, with nothing thrown and nothing logged. Renaming the field on the wire
converts that silent error into an absent field. That is the intent, and it is a **breaking change**
for every consumer below. Per the compatibility rule in `docs/api/redemption.md`, a change to a
field's name is a major bump and has to be announced before it ships.

---

## Upstream consumer — `order-service`

- **Repository path:** `/Users/skhandelwal7/Documents/atlassian/conversational-ai-platform/order-service`
- **Declared in:** `archetype-descriptor.yaml` → `consumers:` → `order-service`, `tier: 1`
- **Pinned to:** **`2.4.0`** of `docs/api/redemption.md` — the version where `discount` was still
  major units
- **Direction:** calls `POST /v1/redemptions` on coupon-service and reads the receipt

### Files in `order-service` that consume this field

| File | How it depends on `discount` |
|---|---|
| `src/main/java/com/beaconstone/order/coupon/RedemptionView.java` | The DTO. Declares `BigDecimal discount` and carries `@JsonIgnoreProperties(ignoreUnknown = true)`, so a renamed field deserialises as **`null`** rather than failing |
| `src/main/java/com/beaconstone/order/pricing/OrderTotalCalculator.java` | Subtracts `discount` directly from the order subtotal. Its own javadoc states there is **no sanity check on the magnitude** and that if the field stopped being an absolute amount "every number here would still be arithmetically valid… It has to hold on the producing side" |
| `src/main/java/com/beaconstone/order/coupon/CouponClient.java` | Fetches the receipt |
| `src/main/java/com/beaconstone/order/fulfilment/ReleaseGate.java` | Gates release on `status`; reached on the same response |
| `src/main/java/com/beaconstone/order/receipt/ReceiptTemplateSelector.java` | Reads `fundingNetwork` from the same response |

**Effect of this change on `order-service`, unmitigated:** `RedemptionView.discount` becomes `null`.
`OrderTotalCalculator.payableTotal` then operates on a null amount. The 100×-too-large discount it
reads today stops, which is the point — but it is replaced by a failure, not by a correct figure.
**`order-service` needs a coordinated change to read `discountMinorUnits` and divide by 100.**

---

## Downstream dependency — `billing-service`

- **Repository path:** `/Users/skhandelwal7/Documents/atlassian/conversational-ai-platform/billing-service`
- **Declared in:** `archetype-descriptor.yaml` → `dependencies:` → `billing-service`
- **Direction:** coupon-service calls it to charge; it also **consumes coupon-service's redemption
  event**, which carries the same field

### Files in `billing-service` that consume this field

| File | How it depends on `discount` |
|---|---|
| `src/main/java/com/beaconstone/billing/event/RedemptionCompletedEvent.java` | The event DTO carrying `discount` |
| `src/main/java/com/beaconstone/billing/event/RedemptionCompletedListener.java` | Computes the interchange rebate as `event.discount().multiply(rateFor(network))`, so the booked rebate **scales linearly with this field** |
| `src/main/java/com/beaconstone/billing/reconciliation/InterchangeRates.java` | Supplies the rate used above |
| `src/main/java/com/beaconstone/billing/reconciliation/SettlementFileBuilder.java` | Builds the settlement artefact the rebate feeds |

**Effect of this change on `billing-service`, unmitigated:** `event.discount()` becomes `null` and
the rebate calculation fails on the first discounted redemption after deploy. Today it books a
rebate computed from a figure 100× too large, so the current state is a wrong number and the new
state is a refusal.

---

## Sequencing

This change is **not independently deployable**. Both consumers read a field that will no longer be
present under that name.

1. Update `order-service` and `billing-service` to read `discountMinorUnits`, tolerating both names
   during the transition.
2. Deploy both consumers.
3. Deploy this change.
4. Remove the transitional handling in the consumers.

Deploying step 3 first replaces a silent 100× error with an outage on the discounted-order path in
`order-service` and a failed rebate booking in `billing-service`.

## Contract announcement

`docs/api/redemption.md` states that a change to a field's name, type or meaning is a major bump
that has to be announced before it ships. This change has not been announced to the two consumers
above, and that announcement is a prerequisite rather than a follow-up.
