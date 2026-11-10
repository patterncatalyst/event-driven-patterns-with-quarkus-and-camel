package com.patterncatalyst.shipping.crudvsevents;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.patterncatalyst.shipping.ConsignmentStatus;

/** What each fact in a Consignment's stream says, stored as jsonb. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConsignmentEventPayload.ConsignmentBooked.class, name = "ConsignmentBooked"),
    @JsonSubTypes.Type(value = ConsignmentEventPayload.ConsignmentStatusChanged.class, name = "ConsignmentStatusChanged")
})
public sealed interface ConsignmentEventPayload {

    record ConsignmentBooked(String id, String origin, String destination, BigDecimal weightKg, String carrier)
            implements ConsignmentEventPayload {
    }

    record ConsignmentStatusChanged(ConsignmentStatus from, ConsignmentStatus to)
            implements ConsignmentEventPayload {
    }
}
