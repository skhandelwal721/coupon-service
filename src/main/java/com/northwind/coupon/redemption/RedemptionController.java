package com.northwind.coupon.redemption;

import com.northwind.coupon.audit.RedemptionAuditor;
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
 */
@RestController
@RequestMapping("/v1/redemptions")
public class RedemptionController {

    private final RedemptionService redemptionService;

    public RedemptionController(RedemptionService redemptionService) {
        this.redemptionService = redemptionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RedemptionReceipt redeem(@Valid @RequestBody RedemptionRequest request) {
        return redemptionService.redeem(request);
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
