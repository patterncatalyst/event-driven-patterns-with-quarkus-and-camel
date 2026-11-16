package com.patterncatalyst.shipping.cache;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.OrderStatus;

/** The full state of one order at one version: the event payload, the cache value and the answer of the read endpoint. */
public record OrderView(String orderId, String customerId, String itemSku, int quantity, BigDecimal amount,
        OrderStatus status, long version) {
}
