package com.patterncatalyst.shipping.resequencing;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/** The batch form: collects up to a size or a time, sorts what it has by order and sequence, and releases the batch. */
@ApplicationScoped
public class BatchResequencerRoute extends RouteBuilder {

    @ConfigProperty(name = "resequencer.batch-size")
    int batchSize;
    @ConfigProperty(name = "resequencer.batch-timeout")
    long batchTimeout;

    @Override
    public void configure() {
        from("direct:batch")
                .routeId("resequence-batch")
                .resequence(header("sortKey")).batch().size(batchSize).timeout(batchTimeout)
                .to("direct:apply");
    }
}
