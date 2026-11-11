package com.patterncatalyst.shipping.ordering;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Header;

import com.patterncatalyst.shipping.Carrier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/** Turns one order into its three shipment events, numbered per order and across the stream. */
@ApplicationScoped
@Named("shipmentPlanner")
public class ShipmentPlanner {

    static final List<String> LIFECYCLE = List.of("ShipmentScheduled", "ShipmentInTransit", "ShipmentDelivered");

    private final AtomicLong stream = new AtomicLong();

    public List<ShipmentEvent> plan(@Header("orderId") String orderId, @Header("carrier") String carrier) {
        Carrier chosen = carrier == null || carrier.isBlank() ? Carrier.FASTLINE : Carrier.valueOf(carrier);
        return java.util.stream.IntStream.range(0, LIFECYCLE.size())
                .mapToObj(i -> new ShipmentEvent(orderId, LIFECYCLE.get(i), i + 1, stream.incrementAndGet(), chosen))
                .toList();
    }
}
