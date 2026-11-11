package com.patterncatalyst.shipping.eventnotification.order;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.kafka.KafkaConstants;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

/** Hands the event to the publishing route once the order's transaction has committed. */
@ApplicationScoped
public class OrderPlacedPublisher {

    private final ProducerTemplate producer;

    OrderPlacedPublisher(ProducerTemplate producer) {
        this.producer = producer;
    }

    void onPlaced(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrderPlaced event) {
        producer.sendBodyAndHeader(OrderPlacedRoute.ENDPOINT, event, KafkaConstants.KEY, event.orderId());
    }
}
