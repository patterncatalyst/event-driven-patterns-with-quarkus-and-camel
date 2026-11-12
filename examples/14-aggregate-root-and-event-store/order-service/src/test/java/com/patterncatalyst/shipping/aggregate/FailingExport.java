package com.patterncatalyst.shipping.aggregate;

import java.util.concurrent.atomic.AtomicBoolean;

import io.debezium.outbox.quarkus.ExportedEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Test hook: fails the command's transaction after the event is appended and the outbox row is written. It
 * observes later than the outbox extension (higher priority number), so both rows exist when it throws.
 */
@ApplicationScoped
public class FailingExport {

    static final AtomicBoolean FAIL = new AtomicBoolean();

    void onExport(@Observes @Priority(3000) ExportedEvent<?, ?> event) {
        if (FAIL.get()) {
            throw new IllegalStateException("failure after the append and the outbox row");
        }
    }
}
