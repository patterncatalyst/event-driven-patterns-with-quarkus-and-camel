package com.patterncatalyst.shipping.evolution;

import java.util.List;

import com.patterncatalyst.shipping.OrderStatus;

/** The order rebuilt from its stream. It sees events in the current shape only, whatever shape they were stored in. */
public final class OrderAggregate {

    private int version;
    private OrderStatus status;
    private String shippingPriority;

    private OrderAggregate() {
    }

    public static OrderAggregate rehydrate(List<EventStore.Loaded> stream) {
        OrderAggregate order = new OrderAggregate();
        for (EventStore.Loaded loaded : stream) {
            switch (loaded.event()) {
                case OrderEvent.OrderPlaced placed -> {
                    order.status = OrderStatus.PLACED;
                    order.shippingPriority = placed.shippingPriority();
                }
                case OrderEvent.OrderCancelled cancelled -> order.status = OrderStatus.CANCELLED;
            }
            order.version = loaded.version();
        }
        return order;
    }

    public int version() {
        return version;
    }

    public OrderStatus status() {
        return status;
    }

    public String shippingPriority() {
        return shippingPriority;
    }
}
