package com.northwind.coupon.redemption;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk redemption.
 *
 * <p>Marketing runs win-back campaigns that redeem a promotion across a whole segment in one
 * go. Doing that a request at a time from the campaign tool was taking hours, so this takes a
 * batch and walks it.
 *
 * <p>Partial success is expected on a batch this size, so the receipt status is
 * {@code REDEEMED_PARTIAL} when some entries fail and {@code REDEEMED} when all of them
 * succeed.
 */
@RestController
@RequestMapping("/v1/redemptions/bulk")
public class BulkRedemptionController {

    /**
     * Largest batch we accept in one request. Sized from the biggest segment the campaign tool
     * has sent us so far, with room to grow.
     */
    static final int MAX_BATCH = 5000;

    private static final Logger log = LoggerFactory.getLogger(BulkRedemptionController.class);

    private final RedemptionService redemptionService;

    public BulkRedemptionController(RedemptionService redemptionService) {
        this.redemptionService = redemptionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BulkRedemptionResult redeemAll(@Valid @RequestBody BulkRedemptionRequest request) {
        List<RedemptionReceipt> redeemed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (RedemptionRequest entry : request.redemptions()) {
            try {
                redeemed.add(redemptionService.redeem(entry));
            } catch (RuntimeException e) {
                failed.add(entry.couponCode() + "/" + entry.invoiceId() + ": " + e.getMessage());
            }
        }

        log.info("bulk redemption complete requested={} redeemed={} failed={}",
                request.redemptions().size(), redeemed.size(), failed.size());

        return new BulkRedemptionResult(redeemed, failed);
    }

    public record BulkRedemptionRequest(
            @NotEmpty(message = "redemptions is required")
            @Size(max = MAX_BATCH, message = "a batch is at most " + MAX_BATCH + " redemptions")
            List<@Valid RedemptionRequest> redemptions
    ) {
    }

    public record BulkRedemptionResult(
            List<RedemptionReceipt> redeemed,
            List<String> failed
    ) {
    }
}
