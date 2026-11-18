package com.patterncatalyst.shipping.batch.worker;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.patterncatalyst.shipping.Carrier;

/** Counts orders, not records, so a record read twice or published twice counts once. */
final class Tally {

    record Counts(long scheduled, long delivered, long open) {
    }

    private final Map<String, Carrier> scheduled = new HashMap<>();
    private final Map<String, Carrier> delivered = new HashMap<>();
    long records;

    void scheduled(String orderId, Carrier carrier) {
        scheduled.putIfAbsent(orderId, carrier);
    }

    void delivered(String orderId, Carrier carrier) {
        delivered.putIfAbsent(orderId, carrier);
    }

    /** Every carrier appears, with zeros when nothing was counted for it. */
    Map<Carrier, Counts> counts() {
        Map<Carrier, Counts> out = new EnumMap<>(Carrier.class);
        for (Carrier carrier : Carrier.values()) {
            Set<String> s = new HashSet<>();
            scheduled.forEach((id, c) -> {
                if (c == carrier) {
                    s.add(id);
                }
            });
            long d = delivered.entrySet().stream().filter(e -> carrierOfDelivery(e.getKey(), e.getValue()) == carrier)
                    .count();
            long open = s.stream().filter(id -> !delivered.containsKey(id)).count();
            out.put(carrier, new Counts(s.size(), d, open));
        }
        return out;
    }

    /** A delivery names its carrier; if it does not, the order's scheduled record does. */
    private Carrier carrierOfDelivery(String orderId, Carrier named) {
        return named != null ? named : scheduled.get(orderId);
    }
}
