package com.patterncatalyst.shipping.evolution.notification;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.patterncatalyst.shipping.evolution.avro.OrderPlaced;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * OrderPlaced as the v2 reader schema sees it. Records written with v1 have no shippingPriority; Avro schema
 * resolution fills in the default the reader schema declares.
 */
@ApplicationScoped
public class OrderPlacedConsumer {

    @Inject
    Recorder recorder;

    @Incoming("placed")
    @Blocking
    public void on(OrderPlaced placed) {
        recorder.record(placed, "smallrye");
    }
}
