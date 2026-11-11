package com.patterncatalyst.shipping.tuning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A catalog event reduced to what a notification needs: which order, which customer, which event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationRequest(String orderId, String customerId, String eventType) {
}
