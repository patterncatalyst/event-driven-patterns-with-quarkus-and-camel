package com.patterncatalyst.shipping.orderservice;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

/** What one committed change leaves behind: the Order, its status and, if any, the stored claim. */
record Stored(Order order, OrderStatus status, Claim claim, String inlineAttachment) {
}
