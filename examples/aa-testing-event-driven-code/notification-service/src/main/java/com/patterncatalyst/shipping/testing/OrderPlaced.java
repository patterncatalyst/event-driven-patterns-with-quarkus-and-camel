package com.patterncatalyst.shipping.testing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The event as the consumer reads it: an event id to deduplicate on and the order id to fetch. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlaced(String eventId, String orderId) {
}
