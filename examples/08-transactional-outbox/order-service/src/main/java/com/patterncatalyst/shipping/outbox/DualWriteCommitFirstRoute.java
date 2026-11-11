package com.patterncatalyst.shipping.outbox;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Topics;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Comparison, not the pattern: commit the order, then publish. A crash between the two leaves an order
 * that no event ever announces.
 */
@ApplicationScoped
public class DualWriteCommitFirstRoute extends RouteBuilder {

    static final String ENDPOINT = "direct:commit-first";

    private final CrashSwitch crash;

    DualWriteCommitFirstRoute(CrashSwitch crash) {
        this.crash = crash;
    }

    @Override
    public void configure() {
        from(ENDPOINT)
                .routeId("dual-write-commit-first")
                .bean("orderService", "save")
                .process(e -> crash.afterCommit(e.getMessage().getBody(Order.class).id()))
                .setBody(e -> OrderPlaced.of(e.getMessage().getBody(Order.class)))
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal().json()
                .toF("kafka:%s", Topics.ORDERS_PLACED);
    }
}
