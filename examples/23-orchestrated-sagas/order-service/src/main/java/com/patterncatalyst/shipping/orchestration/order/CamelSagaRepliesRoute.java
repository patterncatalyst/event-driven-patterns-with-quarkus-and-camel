package com.patterncatalyst.shipping.orchestration.order;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The replies of both participants. A reply that carries the Long-Running-Action header belongs to a saga of this
 * form; the others belong to the workflow and are skipped. The saga is moved with its own endpoints.
 */
@ApplicationScoped
public class CamelSagaRepliesRoute extends RouteBuilder {

    static final String SAGA = "Long-Running-Action";

    @Override
    public void configure() {
        from("kafka:" + String.join(",", Topics.INVENTORY_RESERVED, Topics.INVENTORY_INSUFFICIENT,
                Topics.PAYMENTS_PROCESSED, Topics.PAYMENTS_FAILED) + "?groupId=order-service-camel")
                .routeId("camel-saga-replies")
                .filter(header(SAGA).isNotNull())
                .choice()
                    .when(header(KafkaConstants.TOPIC).isEqualTo(Topics.INVENTORY_RESERVED)).to("direct:camel-take-payment")
                    .when(header(KafkaConstants.TOPIC).isEqualTo(Topics.PAYMENTS_PROCESSED)).to("saga:complete")
                    .otherwise().to("saga:compensate")
                .end();
    }
}
