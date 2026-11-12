package com.patterncatalyst.shipping.snapshots;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The internal ledger of one InventoryItem. These are messages about an existing entity and reach no topic; the
 * public catalog event is InventoryReserved on edp.inventory.reserved.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StockEvent.StockReceived.class, name = "StockReceived"),
        @JsonSubTypes.Type(value = StockEvent.StockReserved.class, name = "StockReserved")
})
public sealed interface StockEvent {

    record StockReceived(int quantity) implements StockEvent {
    }

    record StockReserved(String orderId, int quantity) implements StockEvent {
    }
}
