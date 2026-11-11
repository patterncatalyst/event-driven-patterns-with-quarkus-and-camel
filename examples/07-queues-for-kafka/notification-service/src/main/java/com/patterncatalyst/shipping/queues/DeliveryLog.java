package com.patterncatalyst.shipping.queues;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Writes one row per attempt, in its own short transaction, after the gateway call has returned. */
@ApplicationScoped
public class DeliveryLog {

    @Transactional
    public void append(DeliveryRow row) {
        row.persist();
    }
}
