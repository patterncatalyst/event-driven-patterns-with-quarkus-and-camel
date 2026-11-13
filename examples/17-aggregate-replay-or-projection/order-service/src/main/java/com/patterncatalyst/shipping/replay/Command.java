package com.patterncatalyst.shipping.replay;

import com.patterncatalyst.shipping.Carrier;

/** What a caller asks of an order. A command may be refused; an event is a fact. */
public sealed interface Command {

    String orderId();

    record PlaceOrder(String orderId) implements Command {
    }

    record PayOrder(String orderId, String paymentId) implements Command {
    }

    record ShipOrder(String orderId, Carrier carrier) implements Command {
    }

    record CancelOrder(String orderId, String reason) implements Command {
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
