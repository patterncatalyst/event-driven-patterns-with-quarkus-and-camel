package com.patterncatalyst.shipping.offsets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The parts of a Debezium change event this service keeps: the operation, the row after the change and its WAL position. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeEnvelope(String op, Row after, Source source) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(@JsonProperty("item_sku") String itemSku, @JsonProperty("qty_on_hand") int qtyOnHand) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(long lsn) {
    }
}
