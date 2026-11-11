package com.patterncatalyst.shipping.eventnotification.notification;

import java.util.List;

import org.jboss.resteasy.reactive.RestQuery;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/notifications")
public class NotificationResource {

    @GET
    @Transactional
    public List<NotificationView> byOrder(@RestQuery String orderId) {
        return NotificationRow.<NotificationRow>list("orderId", orderId).stream()
                .map(NotificationView::of).toList();
    }
}
