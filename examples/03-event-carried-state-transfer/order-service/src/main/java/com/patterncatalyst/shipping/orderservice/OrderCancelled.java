package com.patterncatalyst.shipping.orderservice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

/** The Order as it stands after cancellation, status CANCELLED. The thin form carries only the id. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderCancelled(String orderId, Order order, OrderStatus status, Claim claim) {

    static OrderCancelled thin(String orderId) {
        return new OrderCancelled(orderId, null, null, null);
    }
}
