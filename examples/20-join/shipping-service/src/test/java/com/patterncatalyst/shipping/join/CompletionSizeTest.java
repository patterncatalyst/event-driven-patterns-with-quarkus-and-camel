package com.patterncatalyst.shipping.join;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

/**
 * Comparison code: a group that completes when it holds two messages, the obvious way to write "both arrived". The
 * correlation key and the strategy are the same as in the route. A duplicate reservation is the second message, so the
 * group completes with no payment in it.
 */
class CompletionSizeTest {

    @Test
    void aCountOfTwoCompletesOnADuplicateReservation() throws Exception {
        List<Object> completed = new CopyOnWriteArrayList<>();
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:facts")
                            .aggregate(header("orderId"), new ReadinessRoute.AddFact())
                                .completionSize(2)
                            .process(exchange -> completed.add(exchange.getIn().getBody()));
                }
            });
            context.start();
            ProducerTemplate template = context.createProducerTemplate();
            template.sendBodyAndHeader("direct:facts", new java.util.LinkedHashMap<>(java.util.Map.of("reserved", "09:00")), "orderId", "O-1");
            template.sendBodyAndHeader("direct:facts", new java.util.LinkedHashMap<>(java.util.Map.of("reserved", "09:00")), "orderId", "O-1");
        }
        assertEquals(1, completed.size());
        assertEquals(java.util.Set.of("reserved"), ((java.util.Map<?, ?>) completed.get(0)).keySet(),
                "completed with a reservation twice and no payment");
    }
}
