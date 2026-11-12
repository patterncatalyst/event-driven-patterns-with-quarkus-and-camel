package com.patterncatalyst.shipping.aggregate;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** ShipmentScheduled becomes a MarkShipped command, with the same treatment of a refusal as payments. */
@ApplicationScoped
public class ShipmentConsumer {

    @Inject
    OrderCommands commands;

    @Incoming("shipments")
    @Blocking
    public void on(ShipmentScheduled scheduled) {
        var shipment = scheduled.shipment();
        try {
            commands.handle(new Command.MarkShipped(shipment.orderId(), shipment.id(), shipment.carrier().name()));
        } catch (CommandRefused refused) {
            // acknowledged; the refusal is already logged and counted
        }
    }
}
