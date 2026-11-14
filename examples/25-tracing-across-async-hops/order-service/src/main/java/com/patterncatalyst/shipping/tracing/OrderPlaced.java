package com.patterncatalyst.shipping.tracing;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.Order;

/** The event payload: the order as it was placed. */
public record OrderPlaced(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount) {

    public static OrderPlaced of(Order order) {
        return new OrderPlaced(order.id(), order.customerId(), order.itemSku(), order.quantity(), order.amount());
    }
}
