package com.patterncatalyst.shipping.aggregate;

import java.util.List;
import java.util.OptionalInt;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.debezium.outbox.quarkus.ExportedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/**
 * One command, one transaction: load the stream, rebuild the aggregate, decide, append at the version that was
 * loaded and, for a public event, write the outbox row. Callers that retry call this bean again, so every attempt
 * is a new transaction that reloads.
 */
@ApplicationScoped
@Named("orderTransaction")
public class OrderTransaction {

    private static final Logger LOG = Logger.getLogger(OrderTransaction.class);
    private static final String STREAM_TYPE = "Order";

    public record Result(int version, List<OrderEvent> events) {
    }

    @Inject
    EventStore store;

    @Inject
    DemoPause pause;

    @Inject
    Event<ExportedEvent<?, ?>> exports;

    @Inject
    ObjectMapper json;

    @Inject
    MeterRegistry meters;

    @Transactional
    public OrderAggregate load(String orderId) {
        return OrderAggregate.rehydrate(store.load(orderId));
    }

    @Transactional
    public Result handle(Command command) {
        return handle(command, OptionalInt.empty());
    }

    /** The Camel adapter: the route hands over the unmarshalled PaymentProcessed. */
    @Transactional
    public Result markPaid(PaymentProcessed processed) {
        var payment = processed.payment();
        return handle(new Command.MarkPaid(payment.orderId(), payment.id(), payment.amount()));
    }

    /**
     * With a client version the append is checked against what the caller saw, not against what is stored now,
     * so a stale caller gets a conflict instead of a re-decision.
     */
    @Transactional
    public Result handle(Command command, OptionalInt clientVersion) {
        OrderAggregate order = load(command.orderId());
        int loaded = order.version();
        LOG.infof("load %s at version %d", command.orderId(), loaded);
        if (clientVersion.isPresent() && clientVersion.getAsInt() != loaded) {
            throw conflict(command, clientVersion.getAsInt());
        }
        List<OrderEvent> events;
        try {
            events = order.decide(command);
        } catch (CommandRefused refused) {
            meters.counter("order.command.refused", "command", command.name()).increment();
            LOG.infof("refused %s %s: %s", command.name(), command.orderId(), refused.getMessage());
            throw refused;
        }
        pause.afterDecide(command.orderId(), command.name(), loaded);
        int expected = clientVersion.orElse(loaded);
        try {
            store.append(STREAM_TYPE, command.orderId(), expected, events);
        } catch (ConcurrencyConflict conflict) {
            meters.counter("order.command.conflicts", "command", command.name()).increment();
            LOG.infof("conflict %s %s at version %d", command.name(), command.orderId(), expected);
            throw conflict;
        }
        events.forEach(event -> export(command.orderId(), event));
        return new Result(expected + events.size(), events);
    }

    private ConcurrencyConflict conflict(Command command, int clientVersion) {
        meters.counter("order.command.conflicts", "command", command.name()).increment();
        return new ConcurrencyConflict(command.orderId(), clientVersion, null);
    }

    private void export(String orderId, OrderEvent event) {
        switch (event) {
            case OrderEvent.OrderPlaced placed -> exports.fire(OrderExports.placed(orderId, placed, json));
            case OrderEvent.OrderCancelled cancelled -> exports.fire(OrderExports.cancelled(orderId, cancelled, json));
            case OrderEvent.OrderPaid paid -> { // internal: stays in the stream
            }
            case OrderEvent.OrderShipped shipped -> { // internal: stays in the stream
            }
        }
    }

    /**
     * Demo code, the negative witness: appends events that were decided against an old version at the current
     * head, without deciding again.
     */
    @Transactional
    public Result reappend(String orderId, List<OrderEvent> decided) {
        int head = load(orderId).version();
        store.append(STREAM_TYPE, orderId, head, decided);
        decided.forEach(event -> export(orderId, event));
        return new Result(head + decided.size(), decided);
    }
}
