package com.patterncatalyst.shipping.publishing;

import java.math.BigDecimal;
import java.time.Instant;

import com.patterncatalyst.shipping.Order;

/** The OrderPlaced event: the order's fields plus the time it was placed. */
public record OrderPlaced(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount,
        Instant placedAt) {

    public static OrderPlaced of(Order order) {
        return new OrderPlaced(order.id(), order.customerId(), order.itemSku(), order.quantity(), order.amount(),
                Instant.now());
    }
}
