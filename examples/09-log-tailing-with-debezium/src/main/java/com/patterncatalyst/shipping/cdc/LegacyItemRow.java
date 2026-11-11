package com.patterncatalyst.shipping.cdc;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A row of the legacy inventory.inventory_item table, with the legacy column names. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LegacyItemRow(
        @JsonProperty("item_sku") String itemSku,
        @JsonProperty("qty_on_hand") Integer qtyOnHand) {

    /** The same row as the embedded engine delivers it: a map keyed by column name. */
    public static LegacyItemRow of(Map<String, Object> columns) {
        return new LegacyItemRow((String) columns.get("item_sku"), (Integer) columns.get("qty_on_hand"));
    }
}
