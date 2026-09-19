package com.beaconstone.coupon.redemption;

import com.beaconstone.coupon.audit.RedemptionAuditor;
import com.beaconstone.coupon.billing.BillingChargeView;
import com.beaconstone.coupon.billing.BillingClient;
import com.beaconstone.coupon.billing.CardNetwork;
import com.beaconstone.coupon.ledger.PromotionLedger;
import com.beaconstone.coupon.promotion.Coupon;
import com.beaconstone.coupon.promotion.CouponRepository;
import com.beaconstone.coupon.promotion.NetworkPromotionRules;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COUPON-610 — the kill switch for the PERCENTAGE redemption path
 * ({@code promotions.euPercentage.redemptionEnabled}).
 *
 * <p>The key property: when the switch is off, a percentage coupon is refused
 * <strong>before any charge</strong>, so billing-service is never called and the new logic path
 * does not run.
 */
class RedemptionServicePercentageKillSwitchTest {

    private final BillingClient billing = Mockito.mock(BillingClient.class);
    private final CouponRepository coupons = Mockito.mock(CouponRepository.class);
    private final NetworkPromotionRules rules = Mockito.mock(NetworkPromotionRules.class);
    private final RedemptionAuditor auditor = Mockito.mock(RedemptionAuditor.class);
    private final PromotionLedger ledger = Mockito.mock(PromotionLedger.class);

    private RedemptionRequest request(String code) {
        return new RedemptionRequest(code, "inv-1001", "4111111111111111", "EUR",
                "NL-1011AB", "dev-1", "203.0.113.7", "shopper@example.com", "DE");
    }

    private Coupon percentageCoupon() {
        return Coupon.percentage("BS-EUP-20", 2000, Coupon.EUR,
                java.util.Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD), java.util.Set.of());
    }

    @Test
    void aPercentageCouponIsRefusedBeforeAnyChargeWhenTheSwitchIsOff() {
        when(coupons.find("BS-EUP-20")).thenReturn(java.util.Optional.of(percentageCoupon()));

        RedemptionService service = new RedemptionService(
                billing, coupons, rules, auditor, ledger, /* percentageRedemptionEnabled */ false);

        assertThrows(RedemptionService.PercentageRedemptionDisabledException.class,
                () -> service.redeem(request("BS-EUP-20")));

        // The kill switch fires before the charge: billing-service is never called, nothing booked.
        verify(billing, never()).charge(anyString(), anyString(), anyString(), any(), any());
        verify(ledger, never()).book(any());
    }

    @Test
    void aPercentageCouponRunsTheNewPathWhenTheSwitchIsOn() {
        when(coupons.find("BS-EUP-20")).thenReturn(java.util.Optional.of(percentageCoupon()));

        // A 50.00 subtotal charge on Visa; 20% => 10.00 (1000 minor units).
        BillingChargeView charge = new BillingChargeView("chg_1", "inv-1001",
                new BigDecimal("50.00"), new BigDecimal("0.00"), new BigDecimal("50.00"),
                "EUR", "VISA", "visa_abc", "SETTLED");
        when(billing.charge(anyString(), anyString(), anyString(), any(), any())).thenReturn(charge);
        // COUPON-610 fix: the percentage path now applies the computed discount to the card via
        // a second billing call. Stub it to return the re-settled charge (50.00 - 10.00 = 40.00).
        BillingChargeView reduced = new BillingChargeView("chg_1", "inv-1001",
                new BigDecimal("40.00"), new BigDecimal("0.00"), new BigDecimal("40.00"),
                "EUR", "VISA", "visa_abc", "SETTLED");
        when(billing.applyPromotionalDiscount(any(), any())).thenReturn(reduced);
        when(rules.isEligible(any(), any())).thenReturn(true);
        when(rules.fundingNetwork(any())).thenReturn(CardNetwork.VISA);

        RedemptionService service = new RedemptionService(
                billing, coupons, rules, auditor, ledger, /* percentageRedemptionEnabled */ true);

        RedemptionReceipt receipt = service.redeem(request("BS-EUP-20"));

        // The new path ran: a charge was taken, the discount was applied to the card, and the
        // (matching) discount booked (20% of 5000 = 1000).
        verify(billing).charge(anyString(), anyString(), anyString(), any(), any());
        verify(billing).applyPromotionalDiscount(any(), any());
        verify(ledger).book(any());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("1000"), receipt.discount());
    }

    @Test
    void aFixedCouponIsUnaffectedByThePercentageSwitch() {
        Coupon fixed = new Coupon("BS-EU-20", new BigDecimal("20.00"), Coupon.EUR,
                java.util.Set.of(CardNetwork.VISA, CardNetwork.MASTERCARD));
        when(coupons.find("BS-EU-20")).thenReturn(java.util.Optional.of(fixed));

        BillingChargeView charge = new BillingChargeView("chg_2", "inv-1001",
                new BigDecimal("50.00"), new BigDecimal("0.00"), new BigDecimal("50.00"),
                "EUR", "VISA", "visa_abc", "SETTLED");
        when(billing.charge(anyString(), anyString(), anyString(), any(), any())).thenReturn(charge);
        when(rules.isEligible(any(), any())).thenReturn(true);
        when(rules.fundingNetwork(any())).thenReturn(CardNetwork.VISA);

        // Switch OFF, but a FIXED coupon must still redeem normally.
        RedemptionService service = new RedemptionService(
                billing, coupons, rules, auditor, ledger, false);

        RedemptionReceipt receipt = service.redeem(request("BS-EU-20"));
        verify(billing).charge(anyString(), anyString(), anyString(), any(), any());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("2000"), receipt.discount());
    }
}
