package com.patterncatalyst.shipping.eventnotification.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Named("notificationRecorder")
public class NotificationRecorder {

    static final String EVENT_TYPE = "OrderPlaced";

    @Transactional
    public void record(String orderId, FetchedOrder order) {
        NotificationRow row = new NotificationRow();
        row.id = UUID.randomUUID().toString();
        row.orderId = orderId;
        row.customerId = order.customerId();
        row.amount = order.amount();
        row.orderStatus = order.status().name();
        row.eventType = EVENT_TYPE;
        row.createdAt = Instant.now();
        row.persist();
    }
}
