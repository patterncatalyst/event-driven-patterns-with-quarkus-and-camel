package com.patterncatalyst.shipping.offsets;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/** The bean the route calls: one envelope becomes one {@link ItemChange} row. */
@ApplicationScoped
@Named("itemChangeRecorder")
public class ItemChangeRecorder {

    @Transactional
    public void record(ChangeEnvelope envelope) {
        ItemChange change = new ItemChange();
        change.op = envelope.op();
        change.sku = envelope.after().itemSku();
        change.qty = envelope.after().qtyOnHand();
        change.lsn = envelope.source().lsn();
        change.receivedAt = Instant.now();
        change.persist();
    }
}
