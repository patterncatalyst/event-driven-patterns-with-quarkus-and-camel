package com.patterncatalyst.shipping.eventnotification.order;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OrderService {

    private final Event<OrderPlaced> placed;

    OrderService(Event<OrderPlaced> placed) {
        this.placed = placed;
    }

    /** Stores the order and announces it. Observers of the transaction's success see it after commit. */
    @Transactional
    public OrderView place(Order order) {
        if (OrderRow.findById(order.id()) != null) {
            throw new DuplicateOrderException(order.id());
        }
        OrderRow row = new OrderRow();
        row.id = order.id();
        row.customerId = order.customerId();
        row.itemSku = order.itemSku();
        row.quantity = order.quantity();
        row.amount = order.amount();
        row.status = OrderStatus.PLACED;
        row.persist();
        placed.fire(OrderPlaced.of(order.id()));
        return OrderView.of(row);
    }

    public OrderView get(String id) {
        return OrderView.of(find(id));
    }

    @Transactional
    public void cancel(String id) {
        OrderRow row = find(id);
        if (!row.status.canMoveTo(OrderStatus.CANCELLED)) {
            throw new IllegalTransitionException(id, row.status);
        }
        row.status = OrderStatus.CANCELLED;
    }

    private static OrderRow find(String id) {
        OrderRow row = OrderRow.findById(id);
        if (row == null) {
            throw new OrderNotFoundException(id);
        }
        return row;
    }
}
