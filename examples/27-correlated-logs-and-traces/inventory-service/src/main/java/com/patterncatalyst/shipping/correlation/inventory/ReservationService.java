package com.patterncatalyst.shipping.correlation.inventory;

import java.util.List;
import java.util.UUID;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.debezium.outbox.quarkus.ExportedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * The reservation step of the saga and its compensation. The reservation row is the state: a release changes a
 * RESERVED row once, and a release that arrives first leaves a RELEASED row, so the reservation that follows is
 * refused. Both operations are safe in any order and any number of times.
 */
@ApplicationScoped
public class ReservationService {

    private static final Logger LOG = Logger.getLogger(ReservationService.class);

    @Inject
    EntityManager em;

    @Inject
    Event<ExportedEvent<?, ?>> events;

    @Inject
    ObjectMapper json;

    @Inject
    MeterRegistry meters;

    /** Holds the stock and says so, or says there is not enough. A refused or repeated order says nothing. */
    @Transactional
    public boolean reserve(UUID eventId, OrderPlaced order) {
        if (!firstTime(eventId)) {
            return false;
        }
        int claimed = em.createNativeQuery("""
                INSERT INTO inventory.reservation (order_id, sku, quantity, state) VALUES (:id, :sku, :qty, 'RESERVED')
                ON CONFLICT (order_id) DO NOTHING""")
                .setParameter("id", order.orderId()).setParameter("sku", order.itemSku()).setParameter("qty", order.quantity())
                .executeUpdate();
        if (claimed == 0) {
            LOG.infof("refused a reservation for %s: inventory already holds a decision for it", order.orderId());
            return false;
        }
        long taken = StockItem.update("quantityOnHand = quantityOnHand - ?1 where sku = ?2 and quantityOnHand >= ?1",
                order.quantity(), order.itemSku());
        if (taken == 1) {
            events.fire(InventoryExports.reserved(order, json));
            LOG.infof("reserved %d of %s for %s", order.quantity(), order.itemSku(), order.orderId());
            return true;
        }
        Reservation.update("state = 'INSUFFICIENT' where orderId = ?1", order.orderId());
        events.fire(InventoryExports.insufficient(order, json));
        return false;
    }

    /** The compensation: puts the stock back if it is held, and records the release either way. */
    @Transactional
    @SuppressWarnings("unchecked")
    public boolean release(UUID eventId, String orderId) {
        if (!firstTime(eventId)) {
            return false;
        }
        int tombstone = em.createNativeQuery("""
                INSERT INTO inventory.reservation (order_id, quantity, state) VALUES (:id, 0, 'RELEASED')
                ON CONFLICT (order_id) DO NOTHING""").setParameter("id", orderId).executeUpdate();
        if (tombstone == 1) {
            LOG.infof("release for %s arrived before its reservation: recorded, the reservation will be refused", orderId);
            return false;
        }
        List<Object[]> held = em.createNativeQuery("""
                UPDATE inventory.reservation SET state = 'RELEASED' WHERE order_id = :id AND state = 'RESERVED'
                RETURNING sku, quantity""").setParameter("id", orderId).getResultList();
        if (held.isEmpty()) {
            LOG.infof("release for %s changed nothing: it holds no stock", orderId);
            return false;
        }
        StockItem.update("quantityOnHand = quantityOnHand + ?1 where sku = ?2", (Integer) held.get(0)[1], (String) held.get(0)[0]);
        meters.counter("inventory.releases").increment();
        LOG.infof("released %d of %s for %s", held.get(0)[1], held.get(0)[0], orderId);
        return true;
    }

    /** The inbox row and the effect commit together: an event id is applied once. */
    private boolean firstTime(UUID eventId) {
        boolean first = em.createNativeQuery("INSERT INTO inventory.inbox (event_id) VALUES (:id) ON CONFLICT (event_id) DO NOTHING")
                .setParameter("id", eventId).executeUpdate() == 1;
        if (!first) {
            LOG.infof("duplicate event %s skipped", eventId);
        }
        return first;
    }
}
