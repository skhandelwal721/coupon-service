package com.northwind.coupon.redemption;

import com.northwind.coupon.analytics.RedemptionAnalyticsClient;
import com.northwind.coupon.audit.RedemptionAuditor;
import com.northwind.coupon.fraud.VelocityGuard;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>Analytics publishing runs after the redemption is complete and is best-effort: it reports
 * failure rather than throwing, because by that point the charge has been taken and there is
 * nothing left to abort.
 */
@RestController
@RequestMapping("/v1/redemptions")
public class RedemptionController {

    private static final Logger log = LoggerFactory.getLogger(RedemptionController.class);

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

        // Best-effort, and deliberately last. The charge has settled and the discount is
        // booked by this point, so the redemption is already irreversible — an analytics
        // outage must not turn a completed redemption into a failed checkout.
        if (!analytics.publish(receipt)) {
            log.warn("redemption completed but was not published to analytics redemptionId={}",
                    receipt.redemptionId());
        }

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
}
