package com.patterncatalyst.shipping.eventnotification.notification;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patterncatalyst.shipping.OrderStatus;

/** The part of order-service's answer a notification needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FetchedOrder(String customerId, BigDecimal amount, OrderStatus status) {
}
