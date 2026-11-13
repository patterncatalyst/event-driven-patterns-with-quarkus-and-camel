package com.patterncatalyst.shipping.replay;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.patterncatalyst.shipping.Carrier;

/** What happened to an order. The stream of these records is the only state the service keeps. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderEvent.OrderPlaced.class, name = "OrderPlaced"),
        @JsonSubTypes.Type(value = OrderEvent.OrderPaid.class, name = "OrderPaid"),
        @JsonSubTypes.Type(value = OrderEvent.OrderShipped.class, name = "OrderShipped"),
        @JsonSubTypes.Type(value = OrderEvent.OrderDelivered.class, name = "OrderDelivered"),
        @JsonSubTypes.Type(value = OrderEvent.OrderCancelled.class, name = "OrderCancelled")
})
public sealed interface OrderEvent {

    record OrderPlaced(String orderId) implements OrderEvent {
    }

    record OrderPaid(String paymentId) implements OrderEvent {
    }

    record OrderShipped(String shipmentId, Carrier carrier) implements OrderEvent {
    }

    record OrderDelivered() implements OrderEvent {
    }

    record OrderCancelled(String reason) implements OrderEvent {
    }

    /** The name stored in the {@code type} column. */
    default String type() {
        return getClass().getSimpleName();
    }
}
