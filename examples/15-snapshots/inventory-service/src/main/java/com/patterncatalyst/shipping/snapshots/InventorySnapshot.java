package com.patterncatalyst.shipping.snapshots;

/**
 * The state of an InventoryItem at one version. inventory.snapshot.schema is raised whenever this record's meaning
 * changes; a stored snapshot with another schema is ignored and the events rebuild it.
 */
public record InventorySnapshot(String sku, int quantityOnHand) {
}
