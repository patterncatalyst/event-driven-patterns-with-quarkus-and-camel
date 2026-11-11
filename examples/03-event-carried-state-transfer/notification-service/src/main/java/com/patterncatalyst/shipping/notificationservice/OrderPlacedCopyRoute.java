package com.patterncatalyst.shipping.notificationservice;

import com.patterncatalyst.shipping.Topics;

public class OrderPlacedCopyRoute extends OrderCopyRoute {

    public OrderPlacedCopyRoute() {
        super(Topics.ORDERS_PLACED, "copy-from-order-placed");
    }
}
