package com.patterncatalyst.shipping.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import io.debezium.outbox.quarkus.ExportedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/** Every change is one transaction: the order row with its new version, and the outbox row that carries the full state. */
@ApplicationScoped
public class OrderService {

    private final Event<ExportedEvent<?, ?>> events;
    private final ObjectMapper json;
    private final DemoSwitches demo;

    OrderService(Event<ExportedEvent<?, ?>> events, ObjectMapper json, DemoSwitches demo) {
        this.events = events;
        this.json = json;
        this.demo = demo;
    }

    @Transactional
    public OrderView place(Order order) throws Exception {
        OrderEntity row = new OrderEntity();
        row.id = order.id();
        row.customerId = order.customerId();
        row.itemSku = order.itemSku();
        row.quantity = order.quantity();
        row.amount = order.amount();
        row.status = OrderStatus.PLACED;
        row.version = 1;
        demo.beforeCommit(row.view());
        row.persist();
        events.fire(OrderEvent.placed(row.view(), json));
        return row.view();
    }

    @Transactional
    public OrderView cancel(String orderId) {
        OrderEntity row = OrderEntity.findById(orderId);
        if (row == null) {
            throw new NotFoundException();
        }
        if (!row.status.canMoveTo(OrderStatus.CANCELLED)) {
            throw new WebApplicationException("order cannot be cancelled in status " + row.status, Response.Status.CONFLICT);
        }
        row.status = OrderStatus.CANCELLED;
        row.version++;
        events.fire(OrderEvent.cancelled(row.view(), json));
        return row.view();
    }

    public OrderView fromDatabase(String orderId) {
        OrderEntity row = OrderEntity.findById(orderId);
        if (row == null) {
            throw new NotFoundException();
        }
        return row.view();
    }
}
