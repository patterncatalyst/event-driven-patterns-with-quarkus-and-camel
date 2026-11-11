package com.patterncatalyst.shipping.crudvsevents;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** What each fact in an Order's stream says, stored as jsonb. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderEventPayload.OrderPlaced.class, name = "OrderPlaced"),
    @JsonSubTypes.Type(value = OrderEventPayload.OrderPaid.class, name = "OrderPaid"),
    @JsonSubTypes.Type(value = OrderEventPayload.OrderShipped.class, name = "OrderShipped"),
    @JsonSubTypes.Type(value = OrderEventPayload.OrderDelivered.class, name = "OrderDelivered"),
    @JsonSubTypes.Type(value = OrderEventPayload.OrderCancelled.class, name = "OrderCancelled")
})
public sealed interface OrderEventPayload {

    record OrderPlaced(String id, String customerId, String itemSku, int quantity, BigDecimal amount)
            implements OrderEventPayload {
    }

    record OrderPaid() implements OrderEventPayload {
    }

    record OrderShipped() implements OrderEventPayload {
    }

    record OrderDelivered() implements OrderEventPayload {
    }

    record OrderCancelled(String reason) implements OrderEventPayload {
    }
}
