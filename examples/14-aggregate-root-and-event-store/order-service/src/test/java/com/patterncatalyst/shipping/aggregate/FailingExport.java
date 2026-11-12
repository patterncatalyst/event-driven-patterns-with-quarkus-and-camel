package com.patterncatalyst.shipping.aggregate;

import java.util.concurrent.atomic.AtomicBoolean;

import io.debezium.outbox.quarkus.ExportedEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * Test hook: fails the command's transaction after the event is appended and the outbox row is written. It
 * observes later than the outbox extension (higher priority number), so both rows exist when it throws.
 */
@ApplicationScoped
public class FailingExport {

    static final AtomicBoolean FAIL = new AtomicBoolean();

    @Inject
    EntityManager em;

    void onExport(@Observes @Priority(3000) ExportedEvent<?, ?> event) {
        if (FAIL.get()) {
            em.flush();
            Number rows = (Number) em
                    .createNativeQuery("select count(*) from orders.outboxevent where aggregateid = ?1")
                    .setParameter(1, String.valueOf(event.getAggregateId())).getSingleResult();
            if (rows.intValue() != 1) {
                throw new AssertionError("expected the outbox row to exist before the failure, found " + rows);
            }
            throw new IllegalStateException("failure after the append and the outbox row");
        }
    }
}
