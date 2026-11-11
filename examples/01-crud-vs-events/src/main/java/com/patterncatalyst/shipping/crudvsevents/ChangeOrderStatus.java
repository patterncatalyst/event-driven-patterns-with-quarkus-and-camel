package com.patterncatalyst.shipping.crudvsevents;

import com.patterncatalyst.shipping.OrderStatus;

/**
 * The command: move an Order to a new status. {@code expectedVersion} is the stream version the
 * caller last saw; null means "whatever is current". {@code reason} is used only by a cancellation.
 */
public record ChangeOrderStatus(String orderId, OrderStatus to, Integer expectedVersion, String reason) {
}
