package com.patterncatalyst.shipping.notificationservice;

import java.time.Instant;
import java.util.UUID;

import org.apache.camel.Body;

import com.patterncatalyst.shipping.Order;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class OrderCopies {

    /**
     * Upserts the copy from an event. An event that moves the status backwards, such as a late OrderPlaced
     * after OrderCancelled, is ignored. An event without state is refused.
     */
    @Transactional
    public void apply(@Body OrderStateEvent event) {
        Order order = event.order();
        if (order == null || event.status() == null) {
            throw new IllegalArgumentException("event for " + event.orderId() + " carries no order state");
        }
        OrderCopy copy = OrderCopy.findById(order.id());
        if (copy == null) {
            copy = new OrderCopy();
            copy.id = order.id();
        } else if (copy.status != event.status() && !copy.status.canMoveTo(event.status())) {
            return;
        }
        copy.customerId = order.customerId();
        copy.itemSku = order.itemSku();
        copy.quantity = order.quantity();
        copy.amount = order.amount();
        copy.status = event.status();
        if (event.claim() != null) {
            copy.attachmentClaimId = UUID.fromString(event.claim().id());
            copy.attachmentSize = event.claim().size();
        }
        copy.updatedAt = Instant.now();
        copy.persist();
    }

    @Transactional
    public OrderCopy find(String id) {
        OrderCopy copy = OrderCopy.findById(id);
        if (copy == null) {
            throw new NotFoundException("no copy of order " + id);
        }
        return copy;
    }
}
