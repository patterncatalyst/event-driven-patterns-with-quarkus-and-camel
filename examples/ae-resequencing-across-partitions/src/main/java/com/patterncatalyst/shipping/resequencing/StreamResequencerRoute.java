package com.patterncatalyst.shipping.resequencing;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * The stream form: releases an event as soon as its predecessor has been released, or when the timeout passes.
 * It needs integer sequence numbers without gaps across everything it sees.
 */
@ApplicationScoped
public class StreamResequencerRoute extends RouteBuilder {

    @ConfigProperty(name = "resequencer.capacity")
    int capacity;
    @ConfigProperty(name = "resequencer.timeout")
    long timeout;
    @ConfigProperty(name = "resequencer.reject-old")
    boolean rejectOld;

    @Override
    public void configure() {
        onException(Exception.class).handled(true)
                .log(LoggingLevel.WARN, "rejected ${header.orderId} g=${body.globalSequence}: ${exception.class.simpleName}");

        var stream = from("direct:stream")
                .routeId("resequence-stream")
                .resequence(header("seq")).stream().capacity(capacity).timeout(timeout);
        if (rejectOld) {
            stream = stream.rejectOld();
        }
        stream.to("direct:apply");
    }
}
