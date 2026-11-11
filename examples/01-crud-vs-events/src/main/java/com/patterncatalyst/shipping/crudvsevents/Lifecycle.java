package com.patterncatalyst.shipping.crudvsevents;

import com.patterncatalyst.shipping.OrderStatus;

/** PLACED, PAID, SHIPPED, DELIVERED, one step at a time; CANCELLED only from PLACED or PAID. */
final class Lifecycle {

    private Lifecycle() {
    }

    static boolean allows(OrderStatus from, OrderStatus to) {
        return from.canMoveTo(to);
    }
}
