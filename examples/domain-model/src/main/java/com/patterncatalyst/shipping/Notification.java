package com.patterncatalyst.shipping;

/** A message to a customer about an order event. */
public record Notification(String id, String orderId, String customerId, String eventType) {

    public Notification {
        Checks.text(id, "id");
        Checks.text(orderId, "orderId");
        Checks.text(customerId, "customerId");
        Checks.text(eventType, "eventType");
    }
}
