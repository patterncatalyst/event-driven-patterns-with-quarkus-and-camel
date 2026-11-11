package com.patterncatalyst.shipping.notificationservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

/**
 * What this service reads from OrderPlaced and OrderCancelled: the Order, its status and an optional claim.
 * Fields it does not read, such as inline content, are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderStateEvent(String orderId, Order order, OrderStatus status, Claim claim) {
}
