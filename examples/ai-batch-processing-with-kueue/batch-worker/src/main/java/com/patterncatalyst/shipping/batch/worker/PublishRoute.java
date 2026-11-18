package com.patterncatalyst.shipping.batch.worker;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.dataformat.JsonLibrary;

import jakarta.enterprise.context.ApplicationScoped;

/** One record per carrier on the output topic, keyed runId:carrier, so a republished total replaces its predecessor. */
@ApplicationScoped
public class PublishRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:publish-reconciliations").routeId("publish-reconciliations")
                .split(body())
                    .process(e -> {
                        var r = e.getMessage().getBody(CarrierReconciliation.class);
                        e.getMessage().setHeader(KafkaConstants.KEY, r.runId() + ":" + r.carrier());
                    })
                    .marshal().json(JsonLibrary.Jackson)
                    .to("kafka:edp.shipping.reconciliations")
                .end();
    }
}
