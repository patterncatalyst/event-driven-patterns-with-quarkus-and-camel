package com.patterncatalyst.shipping.outbox;

import java.net.URI;

import org.apache.camel.ProducerTemplate;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.Order;

import jakarta.transaction.RollbackException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/orders")
public class OrderResource {

    private final OrderService service;
    private final ProducerTemplate producer;
    private final CrashSwitch crash;

    OrderResource(OrderService service, ProducerTemplate producer, CrashSwitch crash) {
        this.service = service;
        this.producer = producer;
        this.crash = crash;
    }

    @POST
    public RestResponse<Void> place(Order order) {
        service.place(order);
        crash.afterCommit(order.id());
        return created(order);
    }

    /** The comparison: mode is commit-first or publish-first. */
    @POST
    @Path("dual-write")
    public RestResponse<Void> placeByDualWrite(@RestQuery String mode, Order order) {
        producer.sendBody("direct:" + mode, order);
        return created(order);
    }

    private static RestResponse<Void> created(Order order) {
        return RestResponse.created(URI.create("/orders/" + order.id()));
    }

    /** A second order with the same id fails at commit with a unique violation; any other failed commit is a 500. */
    @ServerExceptionMapper
    RestResponse<String> commitFailed(RollbackException e) {
        if (e.getCause() instanceof ConstraintViolationException c
                && c.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
            return RestResponse.status(RestResponse.Status.CONFLICT, "order already exists");
        }
        return RestResponse.status(RestResponse.Status.INTERNAL_SERVER_ERROR, "commit failed");
    }
}
