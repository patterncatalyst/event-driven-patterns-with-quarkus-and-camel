package com.patterncatalyst.shipping.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import io.debezium.outbox.quarkus.ExportedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;

/** Stores the order and the event that announces it in one transaction. */
@ApplicationScoped
public class OrderService {

    private final Event<ExportedEvent<?, ?>> events;
    private final ObjectMapper json;

    OrderService(Event<ExportedEvent<?, ?>> events, ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    @Transactional
    public void place(Order order) {
        OrderEntity row = new OrderEntity();
        row.id = order.id();
        row.customerId = order.customerId();
        row.itemSku = order.itemSku();
        row.quantity = order.quantity();
        row.amount = order.amount();
        row.status = OrderStatus.PLACED;
        row.persist();
        events.fire(OrderPlacedEvent.of(OrderPlaced.of(order), json));
    }
}
