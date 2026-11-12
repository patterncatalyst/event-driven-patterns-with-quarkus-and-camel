package com.patterncatalyst.shipping.snapshots;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

/** The snapshotter's unit of work: load a stream and write its snapshot in a transaction of its own. */
@ApplicationScoped
@Named("snapshots")
public class Snapshots {

    @Inject
    StockLedger ledger;

    @Inject
    SnapshotStore store;

    @Transactional
    public void take(String sku) {
        store.upsert(ledger.load(sku, true).aggregate(), "camel");
    }
}
