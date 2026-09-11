package com.northwind.coupon.event;

import com.northwind.coupon.redemption.RedemptionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Publishes redemption completions to {@code northwind.coupon.redemption.completed}.
 *
 * <p>COUPON-492 moves redemption completion off the synchronous response. The storefront no
 * longer waits on the ledger write and the analytics publish; it gets a {@code PENDING} receipt
 * as soon as the charge has settled, and this event carries the completion.
 *
 * <p>The motivation is latency on the checkout path: the synchronous flow held the customer's
 * request open for the charge, the audit, the eligibility check, the ledger write and the
 * analytics call in series. Only the first of those has to be synchronous.
 *
 * <p>Published at-least-once, as the platform's topic default.
 */
@Component
public class RedemptionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedemptionEventPublisher.class);

    private final String topic;
    private final Clock clock;

    public RedemptionEventPublisher(
            @Value("${events.coupon.redemptionCompletedTopic}") String topic) {
        this(topic, Clock.systemUTC());
    }

    RedemptionEventPublisher(String topic, Clock clock) {
        this.topic = topic;
        this.clock = clock;
    }

    /**
     * Publishes the completion for a redemption.
     *
     * @return the payload as published, for logging and for tests
     */
    public RedemptionCompletedEvent publish(RedemptionReceipt receipt) {
        RedemptionCompletedEvent event = eventFor(receipt);

        // Stubbed for the fixture: the real publisher serializes this and produces to `topic`
        // with the shared platform producer.
        log.info("published redemption completion topic={} eventId={} voucherCode={} network={}",
                topic, event.eventId(), event.voucherCode(), event.network());

        return event;
    }

    RedemptionCompletedEvent eventFor(RedemptionReceipt receipt) {
        return new RedemptionCompletedEvent(
                RedemptionCompletedEvent.EVENT_TYPE,
                "evt_" + UUID.randomUUID(),
                receipt.couponCode(),
                receipt.chargeId(),
                receipt.fundingNetwork(),
                receipt.discount(),
                RedemptionReceipt.REDEEMED,
                Instant.now(clock).toString());
    }
}
