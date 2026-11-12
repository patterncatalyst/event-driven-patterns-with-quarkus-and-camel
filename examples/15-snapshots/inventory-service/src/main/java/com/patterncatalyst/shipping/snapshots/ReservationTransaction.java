package com.patterncatalyst.shipping.snapshots;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * One command, one transaction: load, decide, append and, when the policy says so, write the snapshot. A separate
 * bean so that the retry in ReservationCommands starts a new transaction for every attempt.
 */
@ApplicationScoped
public class ReservationTransaction {

    public record Result(int version, int quantityOnHand) {
    }

    @Inject
    StockLedger ledger;

    @Inject
    SnapshotStore store;

    @Inject
    SnapshotConfig config;

    @Transactional
    public Result reserve(String sku, String orderId, int quantity) {
        return run(sku, aggregate -> aggregate.reserve(orderId, quantity));
    }

    @Transactional
    public Result receive(String sku, int quantity) {
        return run(sku, aggregate -> aggregate.receive(quantity));
    }

    private Result run(String sku, java.util.function.Function<InventoryItemAggregate, StockEvent> decide) {
        var loaded = ledger.load(sku, true);
        var aggregate = loaded.aggregate();
        var event = decide.apply(aggregate);
        ledger.append(sku, aggregate.version() - 1, event);
        int every = config.snapshot().every();
        if (config.snapshot().inline() && every > 0
                && aggregate.version() - loaded.stats().snapshotVersion() >= every) {
            store.upsert(aggregate, "inline");
        }
        return new Result(aggregate.version(), aggregate.quantityOnHand());
    }
}
