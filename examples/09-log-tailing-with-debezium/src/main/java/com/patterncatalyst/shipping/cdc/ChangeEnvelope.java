package com.patterncatalyst.shipping.cdc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The part of a Debezium change event the service reads: the row before and after the change, and the
 * operation (r snapshot read, c create, u update, d delete). {@code before} holds the old row only as far as
 * the table's REPLICA IDENTITY records it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeEnvelope(LegacyItemRow before, LegacyItemRow after, String op) {

    public boolean deleted() {
        return "d".equals(op);
    }

    /** The SKU the change belongs to: the new row, or for a delete the key columns of the old one. */
    public String getSku() {
        return (deleted() ? before : after).itemSku();
    }
}
