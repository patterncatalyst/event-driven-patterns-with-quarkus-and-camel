package com.patterncatalyst.shipping.eventnotification.notification;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

/**
 * Reads OrderPlaced, fetches the order from its source and records a notification.
 * A failed fetch fails the exchange; breakOnFirstError keeps the offset where it is so
 * the record is read again.
 */
public class OrderPlacedNotificationRoute extends RouteBuilder {

    @Override
    public void configure() {
        errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(1000));

        from("kafka:" + Topics.ORDERS_PLACED + "?groupId=notification-service&breakOnFirstError=true")
                .routeId("order-placed-notification")
                .unmarshal().json(OrderPlaced.class)
                .setHeader("orderId", simple("${body.orderId}"))
                .setBody(constant(null))
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD("{{order-service.url}}/orders/${header.orderId}")
                .unmarshal().json(FetchedOrder.class)
                .bean("notificationRecorder", "record(${header.orderId}, ${body})");
    }
}
