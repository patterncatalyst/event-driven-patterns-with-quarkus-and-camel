package com.patterncatalyst.shipping.crudvsevents;

import com.patterncatalyst.shipping.OrderStatus;

/** Request body for a status change; the Order id comes from the path. */
public record StatusRequest(OrderStatus to, Integer expectedVersion, String reason) {
}
