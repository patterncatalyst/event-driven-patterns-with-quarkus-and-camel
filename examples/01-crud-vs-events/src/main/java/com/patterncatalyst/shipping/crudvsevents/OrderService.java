package com.patterncatalyst.shipping.crudvsevents;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** One command, two storage outcomes: update the row in place, append an event to the stream. */
@ApplicationScoped
public class OrderService {

    /** The Order record normalises the amount to two decimals, so the row, the event and the replay agree. */
    @Transactional
    public void place(Order order) {
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

        append(order.id(), 0, new OrderEventPayload.OrderPlaced(order.id(), order.customerId(),
                order.itemSku(), order.quantity(), order.amount()));
    }

    /** Applies the command to both models in one transaction. */
    @Transactional
    public void change(ChangeOrderStatus command) {
        updateRow(command);
        appendEvent(command);
    }

    /** CRUD: the previous status is overwritten. */
    void updateRow(ChangeOrderStatus command) {
        OrderRow row = OrderRow.findById(command.orderId());
        if (row == null) {
            throw new OrderNotFoundException(command.orderId());
        }
        requireAllowed(row.status, command.to());
        row.status = command.to();
    }

    /** Event sourcing: the transition is checked against the replayed state, then a fact is appended. */
    void appendEvent(ChangeOrderStatus command) {
        List<OrderEvent> stream = stream(command.orderId());
        OrderDocument current = OrderDocument.replay(stream);
        if (current == null) {
            throw new OrderNotFoundException(command.orderId());
        }
        requireAllowed(current.status(), command.to());
        int expected = command.expectedVersion() != null ? command.expectedVersion() : stream.size();
        append(command.orderId(), expected, factFor(command));
    }

    private static OrderEventPayload factFor(ChangeOrderStatus command) {
        return switch (command.to()) {
            case PAID -> new OrderEventPayload.OrderPaid();
            case SHIPPED -> new OrderEventPayload.OrderShipped();
            case DELIVERED -> new OrderEventPayload.OrderDelivered();
            case CANCELLED -> new OrderEventPayload.OrderCancelled(
                    command.reason() != null ? command.reason() : "unspecified");
            case PLACED -> throw new IllegalTransitionException("an Order is placed once");
        };
    }

    /** Inserts version expectedVersion + 1; the unique (stream_id, version) constraint rejects a stale writer. */
    void append(String streamId, int expectedVersion, OrderEventPayload payload) {
        OrderEvent event = new OrderEvent();
        event.streamId = streamId;
        event.version = expectedVersion + 1;
        event.type = payload.getClass().getSimpleName();
        event.payload = payload;
        event.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        try {
            event.persistAndFlush();
        } catch (ConstraintViolationException e) {
            throw new ConcurrentAppendException(
                    "stream " + streamId + " already has version " + event.version, e);
        }
    }

    public OrderDocument fromRow(String id) {
        OrderRow row = OrderRow.findById(id);
        if (row == null) {
            throw new OrderNotFoundException(id);
        }
        return OrderDocument.of(row);
    }

    public OrderDocument fromEvents(String id) {
        OrderDocument document = OrderDocument.replay(stream(id));
        if (document == null) {
            throw new OrderNotFoundException(id);
        }
        return document;
    }

    public StatusHistory history(String id) {
        List<OrderEvent> stream = stream(id);
        if (stream.isEmpty()) {
            throw new OrderNotFoundException(id);
        }
        List<StatusHistory.Entry> entries = stream.stream()
                .map(e -> new StatusHistory.Entry(e.version, statusOf(e), e.occurredAt))
                .toList();
        return new StatusHistory(id, entries);
    }

    private static OrderStatus statusOf(OrderEvent event) {
        return switch (event.payload) {
            case OrderEventPayload.OrderPlaced placed -> OrderStatus.PLACED;
            case OrderEventPayload.OrderPaid paid -> OrderStatus.PAID;
            case OrderEventPayload.OrderShipped shipped -> OrderStatus.SHIPPED;
            case OrderEventPayload.OrderDelivered delivered -> OrderStatus.DELIVERED;
            case OrderEventPayload.OrderCancelled cancelled -> OrderStatus.CANCELLED;
        };
    }

    private static List<OrderEvent> stream(String id) {
        return OrderEvent.list("streamId = ?1 order by version", id);
    }

    private static void requireAllowed(OrderStatus from, OrderStatus to) {
        if (!Lifecycle.allows(from, to)) {
            throw new IllegalTransitionException(from + " to " + to + " is not allowed");
        }
    }
}
