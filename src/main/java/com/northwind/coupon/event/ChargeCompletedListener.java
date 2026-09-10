package com.northwind.coupon.event;

import com.northwind.coupon.billing.CardNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code northwind.billing.charge.completed} and attributes each settled charge
 * to the network that funded any promotion applied to it.
 *
 * <p>At-least-once delivery, keyed on {@code chargeId}.
 *
 * <p>Reads {@code cardNetwork}, per billing-service's event contract as of 4.12.0.
 */
@Component
public class ChargeCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(ChargeCompletedListener.class);

    private final String topic;

    public ChargeCompletedListener(
            @Value("${events.billing.chargeCompletedTopic}") String topic) {
        this.topic = topic;
    }

    public void onChargeCompleted(ChargeCompletedEvent event) {
        CardNetwork network = CardNetwork.fromChargeResponse(event.cardNetwork());

        log.info("attributing promotional spend topic={} chargeId={} network={} total={} currency={}",
                topic, event.chargeId(), network, event.total(), event.currency());
    }
}
