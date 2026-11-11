package com.patterncatalyst.shipping.orderservice;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.Order;

/** The request body: an Order plus an optional attachment (a gift message, a document). */
public record PlaceOrderRequest(String id, String customerId, String itemSku, int quantity, BigDecimal amount,
        String attachment) {

    Order toOrder() {
        return new Order(id, customerId, itemSku, quantity, amount);
    }

    boolean hasAttachment() {
        return attachment != null && !attachment.isEmpty();
    }
}
