package com.patterncatalyst.shipping.snapshots;

import java.util.stream.Stream;

/** The aggregate root of one SKU. State is whatever folding its events gives; commands decide, apply changes. */
public final class InventoryItemAggregate {

    private final String sku;
    private int quantityOnHand;
    private int version;

    private InventoryItemAggregate(String sku) {
        this.sku = sku;
    }

    /** No events yet: version 0, nothing on hand. */
    public static InventoryItemAggregate empty(String sku) {
        return new InventoryItemAggregate(sku);
    }

    /** Rebuild from a snapshot taken at {@code version} plus the events after it. */
    public static InventoryItemAggregate from(InventorySnapshot snapshot, int version, Stream<StockEvent> tail) {
        var aggregate = new InventoryItemAggregate(snapshot.sku());
        aggregate.quantityOnHand = snapshot.quantityOnHand();
        aggregate.version = version;
        tail.forEach(aggregate::apply);
        return aggregate;
    }

    /** Rebuild from the first event. */
    public static InventoryItemAggregate replay(String sku, Stream<StockEvent> events) {
        var aggregate = empty(sku);
        events.forEach(aggregate::apply);
        return aggregate;
    }

    public StockEvent receive(int quantity) {
        var event = new StockEvent.StockReceived(quantity);
        apply(event);
        return event;
    }

    public StockEvent reserve(String orderId, int quantity) {
        if (quantityOnHand < quantity) {
            throw new OutOfStock(sku + " has " + quantityOnHand + " on hand, " + quantity + " asked");
        }
        var event = new StockEvent.StockReserved(orderId, quantity);
        apply(event);
        return event;
    }

    public void apply(StockEvent event) {
        switch (event) {
            case StockEvent.StockReceived received -> quantityOnHand += received.quantity();
            case StockEvent.StockReserved reserved -> quantityOnHand -= reserved.quantity();
        }
        version++;
    }

    public InventorySnapshot snapshot() {
        return new InventorySnapshot(sku, quantityOnHand);
    }

    public String sku() {
        return sku;
    }

    public int quantityOnHand() {
        return quantityOnHand;
    }

    public int version() {
        return version;
    }
}
