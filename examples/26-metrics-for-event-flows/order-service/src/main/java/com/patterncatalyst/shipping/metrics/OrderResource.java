package com.patterncatalyst.shipping.metrics;

import java.net.URI;

import org.hibernate.exception.ConstraintViolationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.Order;

import jakarta.transaction.RollbackException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/orders")
public class OrderResource {

    private final OrderService service;

    OrderResource(OrderService service) {
        this.service = service;
    }

    @POST
    public RestResponse<Void> place(Order order) {
        service.place(order);
        return RestResponse.created(URI.create("/orders/" + order.id()));
    }

    /** A second order with the same id fails at commit with a unique violation. */
    @ServerExceptionMapper
    RestResponse<String> commitFailed(RollbackException e) {
        if (e.getCause() instanceof ConstraintViolationException c
                && c.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
            return RestResponse.status(RestResponse.Status.CONFLICT, "order already exists");
        }
        return RestResponse.status(RestResponse.Status.INTERNAL_SERVER_ERROR, "commit failed");
    }
}
