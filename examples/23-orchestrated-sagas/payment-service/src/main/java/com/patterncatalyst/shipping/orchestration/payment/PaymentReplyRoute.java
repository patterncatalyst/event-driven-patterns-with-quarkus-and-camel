package com.patterncatalyst.shipping.orchestration.payment;

import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The reply keeps the saga header, so the Camel orchestrator can tell which saga it belongs to. Every reply is a
 * CloudEvent; one that answers a workflow command also names the instance, which the workflow correlates on.
 */
@ApplicationScoped
public class PaymentReplyRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:reply").routeId("payment-reply")
                .process(e -> {
                    Reply reply = e.getMessage().getBody(Reply.class);
                    e.getMessage().setHeader("type", reply.eventType());
                    e.getMessage().setHeader(KafkaConstants.OVERRIDE_TOPIC, reply.topic());
                    e.getMessage().setHeader("ce_specversion", "1.0");
                    e.getMessage().setHeader("ce_id", UUID.randomUUID().toString());
                    e.getMessage().setHeader("ce_source", "/payment-service");
                    e.getMessage().setHeader("ce_type", reply.eventType());
                    e.getMessage().setHeader("content-type", "application/json");
                    String instance = e.getMessage().getHeader("flowinstanceid", String.class);
                    if (instance != null) {
                        e.getMessage().setHeader("ce_flowinstanceid", instance);
                    }
                })
                .setHeader(KafkaConstants.KEY, header("orderId"))
                .marshal().json(JsonLibrary.Jackson)
                // The topic of the endpoint is overridden per record by the OVERRIDE_TOPIC header.
                .to("kafka:" + Topics.PAYMENTS_PROCESSED);
    }
}
