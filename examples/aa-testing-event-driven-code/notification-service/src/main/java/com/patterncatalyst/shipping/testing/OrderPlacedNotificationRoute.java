package com.patterncatalyst.shipping.testing;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;

import com.patterncatalyst.shipping.Topics;

/**
 * Reads OrderPlaced, fetches the order, and notifies unless the order was cancelled in the meantime. A repeated event
 * id is dropped. The notification leaves on edp.orders.notified, keyed by the order id.
 */
public class OrderPlacedNotificationRoute extends RouteBuilder {

    static final String NOTIFIED = "edp.orders.notified";

    @Override
    public void configure() {
        from("kafka:" + Topics.ORDERS_PLACED + "?groupId=notification-service&breakOnFirstError=true")
                .routeId("notify-order")
                .unmarshal().json(OrderPlaced.class)
                .idempotentConsumer(simple("${body.eventId}"), MemoryIdempotentRepository.memoryIdempotentRepository(1000)) // DEDUP
                .setProperty("event", body())
                .setHeader("orderId", simple("${body.orderId}"))
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD("{{order-service.url}}/orders/${header.orderId}?bridgeEndpoint=true")
                .unmarshal().json(FetchedOrder.class)
                .filter(simple("${body.status} != 'CANCELLED'")) // GUARD
                .bean("notificationLog", "record(${exchangeProperty.event.eventId}, ${header.orderId}, ${body})")
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}")) // KEY
                .marshal().json()
                .to("kafka:" + NOTIFIED);
    }
}
