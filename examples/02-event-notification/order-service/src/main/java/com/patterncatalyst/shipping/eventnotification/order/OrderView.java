package com.patterncatalyst.shipping.eventnotification.order;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.OrderStatus;

/** What GET /orders/{id} returns: the Order's fields and its current status. */
public record OrderView(String id, String customerId, String itemSku, int quantity, BigDecimal amount,
        OrderStatus status) {

    static OrderView of(OrderRow row) {
        return new OrderView(row.id, row.customerId, row.itemSku, row.quantity, row.amount, row.status);
    }
}
