package com.patterncatalyst.shipping.orderservice;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.kafka.common.errors.RecordTooLargeException;

import jakarta.enterprise.context.ApplicationScoped;

/** Publishes after the transaction has committed, through the direct: route that owns the topic. */
@ApplicationScoped
public class OrderPublisher {

    private final ProducerTemplate producer;
    private final OrderEventsConfig config;

    OrderPublisher(ProducerTemplate producer, OrderEventsConfig config) {
        this.producer = producer;
        this.config = config;
    }

    void placed(Stored stored) {
        String id = stored.order().id();
        OrderPlaced event = config.carryState()
                ? new OrderPlaced(id, stored.order(), stored.status(), stored.claim(), stored.inlineAttachment())
                : OrderPlaced.thin(id);
        send(OrderPlacedRoute.DIRECT, event);
    }

    void cancelled(Stored stored) {
        String id = stored.order().id();
        OrderCancelled event = config.carryState()
                ? new OrderCancelled(id, stored.order(), stored.status(), stored.claim())
                : OrderCancelled.thin(id);
        send(OrderCancelledRoute.DIRECT, event);
    }

    private void send(String route, Object event) {
        try {
            producer.sendBody(route, event);
        } catch (CamelExecutionException e) {
            if (e.getCause() instanceof RecordTooLargeException) {
                throw new EventRejectedException("event rejected by the producer: " + e.getCause().getMessage());
            }
            throw e;
        }
    }
}
