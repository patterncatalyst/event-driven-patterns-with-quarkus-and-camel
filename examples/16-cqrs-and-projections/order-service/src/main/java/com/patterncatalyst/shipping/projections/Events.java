package com.patterncatalyst.shipping.projections;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.Payment;
import com.patterncatalyst.shipping.Shipment;

/** The six public events the view is built from. Each is published by the service that owns the fact. */
public final class Events {

    private Events() {
    }

    public record OrderPlaced(Order order) {
    }

    public record OrderCancelled(String orderId, String reason) {
    }

    public record PaymentProcessed(Payment payment) {
    }

    public record ShipmentScheduled(Shipment shipment) {
    }

    public record ShipmentInTransit(Shipment shipment) {
    }

    public record ShipmentDelivered(Shipment shipment) {
    }
}
