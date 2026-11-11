package com.patterncatalyst.shipping.notificationservice;

import com.patterncatalyst.shipping.Topics;

public class OrderCancelledCopyRoute extends OrderCopyRoute {

    public OrderCancelledCopyRoute() {
        super(Topics.ORDERS_CANCELLED, "copy-from-order-cancelled");
    }
}
