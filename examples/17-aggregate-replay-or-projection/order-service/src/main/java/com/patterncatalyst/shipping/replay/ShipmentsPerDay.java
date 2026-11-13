package com.patterncatalyst.shipping.replay;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** One line of the report, as the REST API returns it. */
public record ShipmentsPerDay(String carrier, LocalDate day, long shipments) {

    static List<ShipmentsPerDay> of(Map<CarrierDay, Long> counts) {
        return counts.entrySet().stream()
                .map(e -> new ShipmentsPerDay(e.getKey().carrier(), e.getKey().day(), e.getValue())).toList();
    }
}
