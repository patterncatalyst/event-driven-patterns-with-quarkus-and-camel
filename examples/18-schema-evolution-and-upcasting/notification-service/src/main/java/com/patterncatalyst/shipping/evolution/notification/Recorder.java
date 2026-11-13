package com.patterncatalyst.shipping.evolution.notification;

import com.patterncatalyst.shipping.evolution.avro.OrderPlaced;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/** Records what a reader saw. Both readers call it, so a redelivered record only overwrites its own row. */
@ApplicationScoped
public class Recorder implements PanacheRepositoryBase<RecordedPriority, String> {

    @Transactional
    public void record(OrderPlaced placed, String via) {
        getEntityManager().merge(new RecordedPriority(placed.getOrderId(), placed.getShippingPriority(), via));
    }
}
