package com.patterncatalyst.shipping.aggregate;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** What happened to an order. The stream of these records is the only state the service keeps. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderEvent.OrderPlaced.class, name = "OrderPlaced"),
        @JsonSubTypes.Type(value = OrderEvent.OrderPaid.class, name = "OrderPaid"),
        @JsonSubTypes.Type(value = OrderEvent.OrderShipped.class, name = "OrderShipped"),
        @JsonSubTypes.Type(value = OrderEvent.OrderCancelled.class, name = "OrderCancelled")
})
public sealed interface OrderEvent {

    record OrderPlaced(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount)
            implements OrderEvent {
    }

    record OrderPaid(String paymentId, BigDecimal amount) implements OrderEvent {
    }

    record OrderShipped(String shipmentId, String carrier) implements OrderEvent {
    }

    record OrderCancelled(String reason) implements OrderEvent {
    }

    /** The name stored in the {@code type} column and used as the outbox event type. */
    default String type() {
        return getClass().getSimpleName();
    }
}
