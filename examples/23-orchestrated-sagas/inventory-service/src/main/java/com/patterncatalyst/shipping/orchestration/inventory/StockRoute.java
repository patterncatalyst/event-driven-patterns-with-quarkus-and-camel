package com.patterncatalyst.shipping.orchestration.inventory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.util.UUID;

import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Commands in, saga participation, replies out. ReserveStock joins the saga the command carries (the
 * Long-Running-Action header) and registers its compensation with the coordinator; without the header it runs
 * plain, which is how the workflow form uses it. ReleaseStock is the workflow's explicit undo and calls the same
 * release as the compensation.
 */
@ApplicationScoped
public class StockRoute extends RouteBuilder {

    @Inject
    ReservationService reservations;

    @Override
    public void configure() {
        from("kafka:" + Topics.INVENTORY_COMMANDS + "?groupId=inventory-service").routeId("stock-commands")
                .choice()
                    .when(header("type").isEqualTo("ReserveStock")).to("direct:reserve-stock")
                    .when(header("type").isEqualTo("ReleaseStock")).to("direct:release-stock")
                    .otherwise().log("ignored command ${header.type}")
                .end();

        from("direct:reserve-stock").routeId("reserve-stock")
                .unmarshal().json(JsonLibrary.Jackson, ReserveStock.class)
                .process(e -> e.getMessage().setHeader("orderId", e.getMessage().getBody(ReserveStock.class).orderId()))
                .saga().propagation(SagaPropagation.SUPPORTS)
                    .compensation("direct:release-compensation")
                    .option("orderId", simple("${header.orderId}"))
                .process(e -> reservations.reserve(e.getMessage().getBody(ReserveStock.class))
                        .ifPresentOrElse(e.getMessage()::setBody, () -> e.getMessage().setBody(null)))
                .filter(body().isNotNull())
                .to("direct:reply");

        from("direct:release-stock").routeId("release-stock")
                .unmarshal().json(JsonLibrary.Jackson, ReleaseStock.class)
                .process(e -> reservations.release(e.getMessage().getBody(ReleaseStock.class).orderId()));

        // The coordinator calls this over HTTP with the option set at the join.
        from("direct:release-compensation").routeId("release-compensation")
                .log("compensation: release stock for ${header.orderId}")
                .process(e -> reservations.release(e.getMessage().getHeader("orderId", String.class)));

        // The reply keeps the saga header, so the orchestrator can tell which saga it belongs to, and carries the
        // CloudEvent attributes a workflow instance correlates on when the command named one.
        from("direct:reply").routeId("stock-reply")
                .process(e -> {
                    Reply reply = e.getMessage().getBody(Reply.class);
                    e.getMessage().setHeader("type", reply.eventType());
                    e.getMessage().setHeader(KafkaConstants.OVERRIDE_TOPIC, reply.topic());
                    String instance = e.getMessage().getHeader("flowinstanceid", String.class);
                    if (instance != null) {
                        e.getMessage().setHeader("ce_specversion", "1.0");
                        e.getMessage().setHeader("ce_id", UUID.randomUUID().toString());
                        e.getMessage().setHeader("ce_source", "/inventory-service");
                        e.getMessage().setHeader("ce_type", reply.eventType());
                        e.getMessage().setHeader("ce_flowinstanceid", instance);
                        e.getMessage().setHeader("content-type", "application/json");
                    }
                })
                .setHeader(KafkaConstants.KEY, header("orderId"))
                .marshal().json(JsonLibrary.Jackson)
                // The topic of the endpoint is overridden per record by the OVERRIDE_TOPIC header.
                .to("kafka:" + Topics.INVENTORY_RESERVED);
    }
}
