package com.patterncatalyst.shipping.cdc;

import java.util.Map;

import com.patterncatalyst.shipping.InventoryItem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/** Maps a legacy row to the InventoryItem document. Both capture paths end here. */
@Named("itemDocuments")
@ApplicationScoped
public class ItemDocuments {

    /** The document for a change from Kafka Connect; null for a delete, which is published as a tombstone. */
    public InventoryItem fromEnvelope(ChangeEnvelope change) {
        return change.deleted() ? null : toItem(change.after());
    }

    /** The document for a row from the embedded engine. */
    public InventoryItem fromMap(Map<String, Object> row) {
        return toItem(LegacyItemRow.of(row));
    }

    private InventoryItem toItem(LegacyItemRow row) {
        return new InventoryItem(row.itemSku(), row.qtyOnHand());
    }
}
