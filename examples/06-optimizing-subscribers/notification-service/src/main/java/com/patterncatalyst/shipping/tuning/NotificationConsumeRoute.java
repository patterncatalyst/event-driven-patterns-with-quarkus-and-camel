package com.patterncatalyst.shipping.tuning;

import org.apache.camel.builder.RouteBuilder;

import com.patterncatalyst.shipping.Topics;

/**
 * Reads the three catalog topics the notification service subscribes to. Every consumer setting
 * (max.poll.records, fetch.min.bytes, fetch.max.wait.ms, max.poll.interval.ms, consumersCount and
 * groupProtocol) comes from the camel.component.kafka.* properties, which the environment overrides,
 * so a run changes a setting without a code change.
 */
public class NotificationConsumeRoute extends RouteBuilder {

    @Override
    public void configure() {
        String topics = String.join(",", Topics.ORDERS_PLACED, Topics.PAYMENTS_PROCESSED, Topics.SHIPPING_SCHEDULED);

        from("kafka:" + topics + "?groupId={{notify.group}}&clientId={{notify.client}}")
                .routeId("notification-consume")
                .unmarshal().json(NotificationRequest.class)
                .bean("notificationTemplate", "render")
                .bean("notificationWriter", "write");
    }
}
