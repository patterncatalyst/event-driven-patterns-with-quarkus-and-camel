package com.patterncatalyst.shipping.outbox;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

/** The event payload: the order as it was placed. */
public record OrderPlaced(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount,
        OrderStatus status) {

    public static OrderPlaced of(Order order) {
        return new OrderPlaced(order.id(), order.customerId(), order.itemSku(), order.quantity(), order.amount(),
                OrderStatus.PLACED);
    }
}
