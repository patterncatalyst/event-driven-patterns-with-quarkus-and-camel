package com.patterncatalyst.shipping.evolution;

import java.util.Objects;

import com.patterncatalyst.shipping.Order;

/** What happened to an order, in the current shape. Rows stored in an earlier shape are upcast to these records. */
public sealed interface OrderEvent {

    /** Current shape (schema_version 2). shippingPriority is required: only an upcast row gets a default. */
    record OrderPlaced(Order order, String shippingPriority) implements OrderEvent {
        public OrderPlaced {
            Objects.requireNonNull(shippingPriority,
                    "shippingPriority is missing: a row stored at schema_version 1 must be upcast before it is read");
        }
    }

    record OrderCancelled(String reason) implements OrderEvent {
    }

    /** The name stored in the {@code type} column. */
    default String type() {
        return getClass().getSimpleName();
    }
}
