package com.patterncatalyst.shipping.orderservice;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

/** The order-service's answer to GET /orders/{id}. */
public record OrderDocument(Order order, OrderStatus status) {
}
