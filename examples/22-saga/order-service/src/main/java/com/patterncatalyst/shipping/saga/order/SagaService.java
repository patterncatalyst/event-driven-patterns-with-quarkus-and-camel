package com.patterncatalyst.shipping.saga.order;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import io.debezium.outbox.quarkus.ExportedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * The saga of an order, as local transactions. Every transition is a conditional update on the saga's state, so a
 * reply and the sweeper cannot both win: the loser updates no row and changes nothing. A transition that wins writes
 * its event to the outbox in the same transaction.
 */
@ApplicationScoped
public class SagaService {

    private static final Logger LOG = Logger.getLogger(SagaService.class);

    @Inject
    EntityManager em;

    @Inject
    Event<ExportedEvent<?, ?>> events;

    @Inject
    ObjectMapper json;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "saga.payment-timeout")
    Duration timeout;

    /** Stores the order, starts its saga and announces it. All three commit together or not at all. */
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

        SagaEntity saga = new SagaEntity();
        saga.orderId = order.id();
        saga.state = SagaState.AWAITING_STOCK;
        saga.deadline = Instant.now().plus(timeout);
        saga.persist();

        events.fire(SagaExports.placed(order, json));
    }

    /** InventoryReserved: the saga waits for the payment, and the payment has its own deadline. */
    @Transactional
    public boolean onReserved(UUID eventId, InventoryReserved reserved) {
        if (!firstTime(eventId)) {
            return false;
        }
        return moved(SagaEntity.update("state = ?1, deadline = ?2, version = version + 1 where orderId = ?3 and state = ?4",
                SagaState.AWAITING_PAYMENT, Instant.now().plus(timeout), reserved.orderId(), SagaState.AWAITING_STOCK),
                reserved.orderId(), "reserved");
    }

    /** InventoryInsufficient: nothing was reserved, so the saga ends and nothing is compensated. */
    @Transactional
    public boolean onInsufficient(UUID eventId, InventoryInsufficient insufficient) {
        return firstTime(eventId) && cancel(insufficient.orderId(), SagaState.AWAITING_STOCK, CancelReason.OUT_OF_STOCK);
    }

    /** PaymentProcessed: the saga completes, unless it already ended. */
    @Transactional
    public boolean onPaymentProcessed(UUID eventId, PaymentProcessed processed) {
        if (!firstTime(eventId)) {
            return false;
        }
        String orderId = processed.payment().orderId();
        boolean won = moved(SagaEntity.update("state = ?1, version = version + 1 where orderId = ?2 and state = ?3",
                SagaState.COMPLETED, orderId, SagaState.AWAITING_PAYMENT), orderId, "payment processed");
        if (won) {
            OrderEntity.update("status = ?1 where id = ?2", OrderStatus.PAID, orderId);
            meters.counter("saga.outcomes", "outcome", "completed").increment();
        }
        return won;
    }

    /** PaymentFailed: the saga ends, and OrderCancelled tells inventory to release the stock. */
    @Transactional
    public boolean onPaymentFailed(UUID eventId, PaymentFailed failed) {
        return firstTime(eventId) && cancel(failed.orderId(), SagaState.AWAITING_PAYMENT, CancelReason.PAYMENT_DECLINED);
    }

    /**
     * Cancels every saga whose deadline has passed. The rows are locked with SKIP LOCKED: a saga that a reply is
     * deciding at this moment is left to the reply. Returns how many sagas this call cancelled.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public int expire() {
        List<Object[]> open = em.createNativeQuery("""
                SELECT order_id, state FROM orders.order_saga
                WHERE state IN ('AWAITING_STOCK', 'AWAITING_PAYMENT') AND deadline < now()
                ORDER BY deadline LIMIT 100 FOR UPDATE SKIP LOCKED""").getResultList();
        int cancelled = 0;
        for (Object[] row : open) {
            if (cancel((String) row[0], SagaState.valueOf((String) row[1]), CancelReason.TIMED_OUT)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    private boolean cancel(String orderId, SagaState from, CancelReason reason) {
        boolean won = moved(SagaEntity.update("state = ?1, reason = ?2, version = version + 1 where orderId = ?3 and state = ?4",
                SagaState.CANCELLED, reason, orderId, from), orderId, "cancel " + reason);
        if (won) {
            OrderEntity.update("status = ?1 where id = ?2", OrderStatus.CANCELLED, orderId);
            events.fire(SagaExports.cancelled(orderId, reason, json));
            String outcome = switch (reason) {
                case OUT_OF_STOCK -> "rejected";
                case PAYMENT_DECLINED -> "compensated";
                case TIMED_OUT -> "timed-out";
            };
            meters.counter("saga.outcomes", "outcome", outcome).increment();
        }
        return won;
    }

    private static boolean moved(long updated, String orderId, String what) {
        if (updated == 0) {
            LOG.infof("ignored %s for %s: the saga is not in the state that expects it", what, orderId);
        }
        return updated == 1;
    }

    /** The inbox row and the transition commit together: an event id is applied once. */
    private boolean firstTime(UUID eventId) {
        boolean first = em.createNativeQuery("INSERT INTO orders.inbox (event_id) VALUES (:id) ON CONFLICT (event_id) DO NOTHING")
                .setParameter("id", eventId).executeUpdate() == 1;
        if (!first) {
            LOG.infof("duplicate event %s skipped", eventId);
        }
        return first;
    }
}
