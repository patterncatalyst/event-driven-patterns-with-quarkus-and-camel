package com.patterncatalyst.shipping.eventnotification.notification;

import java.math.BigDecimal;

/** What GET /notifications returns. */
public record NotificationView(String id, String orderId, String customerId, BigDecimal amount,
        String orderStatus, String eventType) {

    static NotificationView of(NotificationRow row) {
        return new NotificationView(row.id, row.orderId, row.customerId, row.amount, row.orderStatus,
                row.eventType);
    }
}
