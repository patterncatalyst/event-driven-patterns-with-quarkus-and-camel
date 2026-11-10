package com.patterncatalyst.shipping.crudvsevents;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

/** The Document Message a reader receives: the Order and where it stands now. */
public record OrderDocument(Order order, OrderStatus status) {

    static OrderDocument of(OrderRow row) {
        return new OrderDocument(
                new Order(row.id, row.customerId, row.itemSku, row.quantity, row.amount), row.status);
    }

    /** Rebuilds the document by folding a stream from the first event to the last. */
    static OrderDocument replay(Iterable<OrderEvent> stream) {
        Order order = null;
        OrderStatus status = null;
        for (OrderEvent event : stream) {
            switch (event.payload) {
                case OrderEventPayload.OrderPlaced placed -> {
                    order = new Order(placed.id(), placed.customerId(), placed.itemSku(), placed.quantity(),
                            placed.amount());
                    status = OrderStatus.PLACED;
                }
                case OrderEventPayload.OrderPaid paid -> status = OrderStatus.PAID;
                case OrderEventPayload.OrderShipped shipped -> status = OrderStatus.SHIPPED;
                case OrderEventPayload.OrderDelivered delivered -> status = OrderStatus.DELIVERED;
                case OrderEventPayload.OrderCancelled cancelled -> status = OrderStatus.CANCELLED;
            }
        }
        return order == null ? null : new OrderDocument(order, status);
    }
}
