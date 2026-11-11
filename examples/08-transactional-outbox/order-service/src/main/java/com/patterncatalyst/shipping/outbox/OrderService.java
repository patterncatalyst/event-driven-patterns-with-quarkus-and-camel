package com.patterncatalyst.shipping.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import io.debezium.outbox.quarkus.ExportedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/** Both methods run in one database transaction, so nothing here can fail halfway. */
@ApplicationScoped
@Named("orderService")
public class OrderService {

    private final Event<ExportedEvent<?, ?>> events;
    private final ObjectMapper json;

    OrderService(Event<ExportedEvent<?, ?>> events, ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    /** Stores the order and the event that announces it. They commit together or not at all. */
    @Transactional
    public void place(Order order) {
        save(order);
        events.fire(OrderPlacedEvent.of(OrderPlaced.of(order), json));
    }

    /** Stores the order alone. The dual-write routes call this and publish separately. */
    @Transactional
    public void save(Order order) {
        OrderEntity row = new OrderEntity();
        row.id = order.id();
        row.customerId = order.customerId();
        row.itemSku = order.itemSku();
        row.quantity = order.quantity();
        row.amount = order.amount();
        row.status = OrderStatus.PLACED;
        row.persist();
    }
}
