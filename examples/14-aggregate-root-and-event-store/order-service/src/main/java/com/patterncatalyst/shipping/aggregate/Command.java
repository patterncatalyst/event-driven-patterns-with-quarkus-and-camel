package com.patterncatalyst.shipping.aggregate;

import java.math.BigDecimal;

/** What a caller asks of an order. A command may be refused; an event is a fact. */
public sealed interface Command {

    String orderId();

    record Place(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount)
            implements Command {
    }

    record MarkPaid(String orderId, String paymentId, BigDecimal amount) implements Command {
    }

    record MarkShipped(String orderId, String shipmentId, String carrier) implements Command {
    }

    record Cancel(String orderId, String reason) implements Command {
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
