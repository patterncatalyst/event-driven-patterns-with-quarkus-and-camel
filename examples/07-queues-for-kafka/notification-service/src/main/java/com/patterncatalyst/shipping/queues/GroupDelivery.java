package com.patterncatalyst.shipping.queues;

import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.component.kafka.KafkaConstants;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/** The bean the consumer-group route calls. A transient failure is thrown so the route's redelivery policy retries it. */
@ApplicationScoped
@Named("groupDelivery")
public class GroupDelivery {

    private final DeliveryService delivery;

    GroupDelivery(DeliveryService delivery) {
        this.delivery = delivery;
    }

    public void deliver(DeliveryJob job,
                        @Header(KafkaConstants.PARTITION) Integer partition,
                        @Header(KafkaConstants.OFFSET) Long offset,
                        @Header(Exchange.REDELIVERY_COUNTER) Integer redeliveries) {
        int attempt = (redeliveries == null ? 0 : redeliveries) + 1;
        if (delivery.attempt("group", job, partition, offset, attempt) == Outcome.FAILED) {
            throw GatewayFailure.transientFailure("attempt " + attempt + " failed for " + job.orderId());
        }
    }
}
