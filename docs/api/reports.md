# Finance reports

## `GET /v1/reports/attribution/{date}`

The daily promotional-spend export, built on demand from the receipts recorded since the last
restart.

Added for COUPON-482. Finance previously had to wait for the overnight warehouse load, which
meant the promotional-spend line could not be closed on the day it happened.

```
GET /v1/reports/attribution/2026-09-10
```

```json
{
  "date": "2026-09-10",
  "rows": [
    { "redemptionId": "rdm_4f8a21c7", "couponCode": "NW-VISA-10", "discount": "24.90" }
  ],
  "total": "24.90",
  "exceptions": []
}
```

| Field | Meaning |
| --- | --- |
| `rows` | one per redemption, in the order they completed |
| `total` | what finance books as the day's promotional spend |
| `exceptions` | redemptions held out of the export because the discount is above `report.maxPlausibleDiscount` |

## `GET /v1/reports/attribution/{date}/total`

The same figure without the rows, for the finance dashboard tile.

```json
{ "date": "2026-09-10", "total": "24.90", "rows": 1 }
```

## The plausibility ceiling

`report.maxPlausibleDiscount` (default `25000.00`) is a **magnitude** cap: a single discount
above it is treated as a data error and held in `exceptions` rather than exported.

It was hard-coded at `1000.00`, which held legitimate enterprise-catalogue rows — a 40% coupon
on a five-figure basket is a real promotion. Raising it clears those.

Note what the cap does **not** do, because it matters when reading the total: there is no
floor. A discount that is too *small* — for any reason — passes straight through and understates
the day. See `AttributionExportTest.aTooSmallDiscountProducesNoException`.

## Retention

Receipts are held in memory by `ReceiptStore` from process start. Practical consequences:

- **A restart empties the export.** A date requested after a deploy returns only the
  redemptions since that deploy. The `date` path variable labels the response; it does not
  filter it.
- Both endpoints return every receipt held, unpaginated.
