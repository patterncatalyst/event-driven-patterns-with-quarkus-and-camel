package com.patterncatalyst.shipping.orderservice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

/**
 * Event-carried state: the whole Order and its status. The thin form carries only the id.
 * The attachment is either a claim (default) or inline content (the comparison variant).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderPlaced(String orderId, Order order, OrderStatus status, Claim claim, String attachment) {

    static OrderPlaced thin(String orderId) {
        return new OrderPlaced(orderId, null, null, null, null);
    }
}
