package com.patterncatalyst.shipping.resequencing;

import com.patterncatalyst.shipping.Carrier;

/**
 * One shipment status change. {@code sequence} numbers the events of one order (1, 2, 3).
 * {@code globalSequence} is assigned by the single sequencer that stamps every event before it is published,
 * and numbers the events of all orders without a gap.
 */
public record ShipmentEvent(String orderId, String type, int sequence, long globalSequence, Carrier carrier) {

    /** The sort key of the batch form: the order id, then the per-order sequence, zero padded. */
    public String sortKey() {
        return "%s:%06d".formatted(orderId, sequence);
    }
}
