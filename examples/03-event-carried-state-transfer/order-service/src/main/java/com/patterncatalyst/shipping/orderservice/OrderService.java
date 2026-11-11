package com.patterncatalyst.shipping.orderservice;

import java.nio.charset.StandardCharsets;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class OrderService {

    private final OrderEventsConfig config;

    OrderService(OrderEventsConfig config) {
        this.config = config;
    }

    /** Stores the order, and its attachment as a claim unless the attachment travels inline. */
    @Transactional
    Stored place(PlaceOrderRequest request) {
        Order order = request.toOrder();
        if (OrderRow.findById(order.id()) != null) {
            throw new ClientErrorException("order " + order.id() + " exists", Response.Status.CONFLICT);
        }
        OrderRow.of(order, OrderStatus.PLACED).persist();
        if (!request.hasAttachment()) {
            return new Stored(order, OrderStatus.PLACED, null, null);
        }
        if (config.attachment() == OrderEventsConfig.AttachmentMode.INLINE) {
            return new Stored(order, OrderStatus.PLACED, null, request.attachment());
        }
        ClaimCheck claim = ClaimCheck.store(order.id(), request.attachment());
        long size = request.attachment().getBytes(StandardCharsets.UTF_8).length;
        return new Stored(order, OrderStatus.PLACED, new Claim(claim.id.toString(), size), null);
    }

    @Transactional
    Stored cancel(String id) {
        OrderRow row = OrderRow.findById(id);
        if (row == null) {
            throw new NotFoundException("no order " + id);
        }
        if (!row.status.canMoveTo(OrderStatus.CANCELLED)) {
            throw new ClientErrorException("cannot cancel an order that is " + row.status, Response.Status.CONFLICT);
        }
        row.status = OrderStatus.CANCELLED;
        Claim claim = ClaimCheck.<ClaimCheck>find("orderId", id).firstResultOptional()
                .map(c -> new Claim(c.id.toString(), c.content.getBytes(StandardCharsets.UTF_8).length))
                .orElse(null);
        return new Stored(row.toOrder(), row.status, claim, null);
    }

    @Transactional
    OrderDocument find(String id) {
        OrderRow row = OrderRow.findById(id);
        if (row == null) {
            throw new NotFoundException("no order " + id);
        }
        return new OrderDocument(row.toOrder(), row.status);
    }

    @Transactional
    String claimContent(String id) {
        ClaimCheck claim = ClaimCheck.findById(java.util.UUID.fromString(id));
        if (claim == null) {
            throw new NotFoundException("no claim " + id);
        }
        return claim.content;
    }
}
