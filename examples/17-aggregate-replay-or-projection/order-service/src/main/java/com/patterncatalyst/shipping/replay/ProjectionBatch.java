package com.patterncatalyst.shipping.replay;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

/**
 * One batch of the projector. The rows it adds and the checkpoint it moves commit together, so a crash repeats
 * nothing and loses nothing.
 */
@ApplicationScoped
public class ProjectionBatch {

    /** A place in the event store: the transaction that wrote the row and its position. */
    public record Position(long txId, long position) {

        boolean reaches(Position other) {
            return txId > other.txId || (txId == other.txId && position >= other.position);
        }
    }

    @Inject
    ProjectionWatermark watermark;

    @Inject
    CarrierDailyStore store;

    @Inject
    EntityManager em;

    /** Applies up to {@code limit} events past the checkpoint and returns how many it read. */
    @Transactional
    public int run(int limit) {
        // Locking the checkpoint row serializes ticks: a second instance waits here and then reads past the first.
        ProjectionCheckpoint checkpoint = ProjectionCheckpoint.findById(ProjectionCheckpoint.CARRIER_DAILY,
                LockModeType.PESSIMISTIC_WRITE);
        List<ProjectionWatermark.Pending> pending = watermark.read(checkpoint.lastTxId, checkpoint.lastPosition, limit);
        if (pending.isEmpty()) {
            return 0;
        }
        Map<CarrierDay, Long> counts = new TreeMap<>();
        for (ProjectionWatermark.Pending event : pending) {
            if ("OrderShipped".equals(event.type())) {
                counts.merge(new CarrierDay(event.carrier(), event.day()), 1L, Long::sum);
            }
        }
        counts.forEach((key, shipped) -> store.add(key.carrier(), key.day(), shipped));
        ProjectionWatermark.Pending last = pending.getLast();
        checkpoint.lastTxId = last.txId();
        checkpoint.lastPosition = last.position();
        checkpoint.updatedAt = java.time.Instant.now();
        return pending.size();
    }

    /** Events past the checkpoint that have committed: the work the projector has left. */
    @Transactional
    public long lagEvents() {
        ProjectionCheckpoint checkpoint = ProjectionCheckpoint.findById(ProjectionCheckpoint.CARRIER_DAILY);
        return ((Number) em.createNativeQuery("""
                SELECT count(*) FROM orders.events WHERE (tx_id, position) > (CAST(CAST(:t AS text) AS xid8), :p)
                """).setParameter("t", checkpoint.lastTxId).setParameter("p", checkpoint.lastPosition)
                .getSingleResult()).longValue();
    }

    @Transactional
    public Position checkpoint() {
        ProjectionCheckpoint checkpoint = ProjectionCheckpoint.findById(ProjectionCheckpoint.CARRIER_DAILY);
        return new Position(checkpoint.lastTxId, checkpoint.lastPosition);
    }

    /** The newest committed place in the store: where a rebuild is done. */
    @Transactional
    public Position head() {
        List<?> rows = em.createNativeQuery("""
                SELECT tx_id::text::bigint, position FROM orders.events ORDER BY tx_id DESC, position DESC LIMIT 1
                """).getResultList();
        if (rows.isEmpty()) {
            return new Position(0, 0);
        }
        Object[] row = (Object[]) rows.getFirst();
        return new Position(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    /** Drops the read model and rewinds the checkpoint to the start, in one transaction. */
    @Transactional
    public void reset() {
        ProjectionCheckpoint checkpoint = ProjectionCheckpoint.findById(ProjectionCheckpoint.CARRIER_DAILY,
                LockModeType.PESSIMISTIC_WRITE);
        CarrierDailyShipments.deleteAll();
        checkpoint.lastTxId = 0;
        checkpoint.lastPosition = 0;
        checkpoint.updatedAt = java.time.Instant.now();
    }
}
