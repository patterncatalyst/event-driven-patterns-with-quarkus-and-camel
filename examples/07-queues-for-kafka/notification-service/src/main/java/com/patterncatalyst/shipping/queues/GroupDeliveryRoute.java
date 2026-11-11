package com.patterncatalyst.shipping.queues;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The same jobs read through an ordinary consumer group: each partition has one owner, which delivers its
 * records one after another. A transient failure is retried in place.
 */
@ApplicationScoped
public class GroupDeliveryRoute extends RouteBuilder {

    @Override
    public void configure() {
        onException(GatewayFailure.class).maximumRedeliveries(3).redeliveryDelay(200).logStackTrace(false);

        from("kafka:" + Topics.ORDERS_PLACED + "?groupId=notification-workers&clientId={{delivery.worker}}")
                .routeId("group-delivery")
                .autoStartup("{{delivery.group.enabled}}")
                .unmarshal().json(DeliveryJob.class)
                .bean("groupDelivery", "deliver");
    }
}
