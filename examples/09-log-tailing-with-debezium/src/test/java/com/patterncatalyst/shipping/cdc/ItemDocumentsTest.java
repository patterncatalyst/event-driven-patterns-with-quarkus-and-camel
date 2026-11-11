package com.patterncatalyst.shipping.cdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.InventoryItem;

/** Both capture paths map a row the same way. */
class ItemDocumentsTest {

    private final ItemDocuments documents = new ItemDocuments();

    @Test
    void aRowFromConnectAndARowFromTheEmbeddedEngineMapToTheSameDocument() {
        var fromConnect = documents.fromEnvelope(new ChangeEnvelope(null, new LegacyItemRow("SKU-1", 12), "u"));
        var fromEngine = documents.fromMap(Map.of("item_sku", "SKU-1", "qty_on_hand", 12, "last_changed", "ignored"));
        assertEquals(new InventoryItem("SKU-1", 12), fromConnect);
        assertEquals(fromConnect, fromEngine);
    }

    @Test
    void aDeleteHasNoDocument() {
        assertNull(documents.fromEnvelope(new ChangeEnvelope(new LegacyItemRow("SKU-1", 0), null, "d")));
    }
}
