package com.patterncatalyst.shipping.eventnotification.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The event as the consumer reads it: the order id is all there is to act on. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlaced(String eventId, String orderId) {
}
