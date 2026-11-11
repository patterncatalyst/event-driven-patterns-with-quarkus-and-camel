package com.patterncatalyst.shipping;

/** Stock on hand for one SKU. */
public record InventoryItem(String sku, int quantityOnHand) {

    public InventoryItem {
        Checks.text(sku, "sku");
        Checks.nonNegative(quantityOnHand, "quantityOnHand");
    }
}
