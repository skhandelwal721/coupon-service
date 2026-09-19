## Summary

Removes the **COUPON-610 EU percentage discount (BS-EUP-20, 20% off)** feature from `main`.

Restores the following to their pre-feature state and removes the feature's doc/tests:
- `Coupon.java`, `CouponRepository.java`, `RedemptionService.java`, `RedemptionController.java`, `BillingClient.java`, `application.yml`
- Removes `docs/design/COUPON-610-eu-percentage-discount.md`
- Removes the percentage / catalogue / money-path / kill-switch tests

Fixed-amount coupons are unchanged; no other functionality is affected.

_Left unmerged for manual merge._
