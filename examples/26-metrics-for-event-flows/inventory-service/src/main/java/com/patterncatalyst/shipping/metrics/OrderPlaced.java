package com.patterncatalyst.shipping.metrics;

/** The event payload this service reads from edp.orders.placed. */
public record OrderPlaced(String orderId, String customerId, String itemSku, int quantity) {
}
