package com.beaconstone.coupon.redemption;

import com.beaconstone.coupon.analytics.RedemptionAnalyticsClient;
import com.beaconstone.coupon.audit.RedemptionAuditor;
import com.beaconstone.coupon.fraud.VelocityGuard;
import com.beaconstone.coupon.payments.AmexEuropeEligibility;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redeems a coupon. Customer-facing: this runs on the storefront checkout path.
 *
 * <p>The velocity check is applied <em>here</em>, not in {@link RedemptionService}, because
 * the service is also driven by the promotions backfill job where velocity has already been
 * assessed over the whole batch. That makes the guard a property of the entrypoint: any new
 * way to redeem has to call {@link VelocityGuard#check} for itself. See
 * {@code docs/runbooks/redemption.md}.
 */
@RestController
@RequestMapping("/v1/redemptions")
public class RedemptionController {

    private final RedemptionService redemptionService;
    private final VelocityGuard velocityGuard;
    private final RedemptionAnalyticsClient analytics;

    public RedemptionController(RedemptionService redemptionService,
                                VelocityGuard velocityGuard,
                                RedemptionAnalyticsClient analytics) {
        this.redemptionService = redemptionService;
        this.velocityGuard = velocityGuard;
        this.analytics = analytics;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RedemptionReceipt redeem(@Valid @RequestBody RedemptionRequest request) {
        velocityGuard.check(request);
        RedemptionReceipt receipt = redemptionService.redeem(request);
        analytics.publish(receipt, request);
        return receipt;
    }

    /** Refused before the charge — no money moved and no discount was booked. */
    @ExceptionHandler(VelocityGuard.VelocityExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public String velocityExceeded(VelocityGuard.VelocityExceededException e) {
        return e.getMessage();
    }

    /**
     * The charge did not satisfy billing-service's published invariant, so we cannot reconcile
     * a discount against it. Held, not applied — the customer sees checkout fail rather than
     * getting a discount we cannot account for.
     */
    @ExceptionHandler(RedemptionAuditor.UnaccountableChargeException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public String unaccountable(RedemptionAuditor.UnaccountableChargeException e) {
        return e.getMessage();
    }

    /**
     * {@code cardType} on the charge was not a network we recognise, so no promotion can be
     * attributed. This surfaces as a 500 deliberately: it means billing-service's contract has
     * moved and there is no safe default.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unattributableNetwork(IllegalArgumentException e) {
        return "cannot resolve the funding network for this charge: " + e.getMessage();
    }

    @ExceptionHandler(RedemptionService.PromotionNotFundedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String notFunded(RedemptionService.PromotionNotFundedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(RedemptionService.UnknownCouponException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String unknownCoupon(RedemptionService.UnknownCouponException e) {
        return e.getMessage();
    }

    /**
     * COUPON-573. The coupon exists but is not offered in the request's country (or the country
     * was absent for a country-restricted coupon). Surfaced as {@code 404}, the same as an
     * unknown coupon: from this storefront's point of view the code is not on offer, and we do
     * not want to signal that it exists on another storefront. Refused before the charge, so no
     * money moved and no discount was booked.
     */
    @ExceptionHandler(RedemptionService.CouponNotAvailableInCountryException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notAvailableInCountry(RedemptionService.CouponNotAvailableInCountryException e) {
        return e.getMessage();
    }

    /**
     * PAY-8100. AMEX was requested where it is not offered — a region outside the European set,
     * with the option disabled, or with no country. Without this handler the
     * {@link AmexEuropeEligibility.AmexNotOfferedException} thrown by the eligibility gate would
     * propagate uncaught and surface to the customer as a generic {@code 500}, even though the
     * request was refused deliberately and safely before any charge.
     *
     * <p>Mapped to {@code 404}, consistent with the country-restricted coupon case above: from
     * the customer's point of view AMEX is simply not on offer here, and no charge was taken.
     */
    @ExceptionHandler(AmexEuropeEligibility.AmexNotOfferedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String amexNotOffered(AmexEuropeEligibility.AmexNotOfferedException e) {
        return e.getMessage();
    }
}
