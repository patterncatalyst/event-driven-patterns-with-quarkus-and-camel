package com.patterncatalyst.shipping.testing;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.patterncatalyst.shipping.Notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/** The recorded notifications. In memory, because the lesson is how the route is tested, not where the rows live. */
@ApplicationScoped
@Named("notificationLog")
public class NotificationLog {

    private final List<Notification> rows = new CopyOnWriteArrayList<>();

    public Notification record(String eventId, String orderId, FetchedOrder order) {
        var notification = new Notification("N-" + eventId, orderId, order.customerId(), "OrderPlaced");
        rows.add(notification);
        return notification;
    }

    public long count(String orderId) {
        return rows.stream().filter(n -> n.orderId().equals(orderId)).count();
    }
}
