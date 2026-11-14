package com.patterncatalyst.shipping.pickups;

import com.patterncatalyst.shipping.Carrier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What the warehouse reads: the shipments one carrier collects, and how many to expect. Both forms write this type.
 * {@code from} and {@code to} are the slot the Kafka Streams form closed; the Camel form groups by arrival and leaves
 * them null. {@code closedBy} says what ended the list: window, update, size or timeout.
 */
public record PickupList(Carrier carrier, Instant from, Instant to, List<String> shipmentIds, int count, String closedBy) {

    /** The aggregate's initial value; the slot's carrier and bounds are filled in when the window leaves. */
    public static PickupList empty() {
        return new PickupList(null, null, null, List.of(), 0, null);
    }

    public PickupList add(String shipmentId) {
        List<String> ids = new ArrayList<>(shipmentIds);
        ids.add(shipmentId);
        return new PickupList(carrier, from, to, ids, ids.size(), closedBy);
    }
}
