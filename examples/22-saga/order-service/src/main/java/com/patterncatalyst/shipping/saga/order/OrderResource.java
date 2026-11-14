package com.patterncatalyst.shipping.saga.order;

import java.net.URI;
import java.time.Instant;

import org.hibernate.exception.ConstraintViolationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import jakarta.inject.Inject;
import jakarta.transaction.RollbackException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/orders")
public class OrderResource {

    @Inject
    SagaService saga;

    /** The order, where its saga is and until when it waits there. */
    public record OrderView(String id, OrderStatus status, SagaState saga, Instant deadline, int version, CancelReason reason) {
    }

    @POST
    public RestResponse<Void> place(Order order) {
        saga.place(order);
        return RestResponse.created(URI.create("/orders/" + order.id()));
    }

    @GET
    @Path("{id}")
    public RestResponse<OrderView> get(@PathParam("id") String id) {
        OrderEntity order = OrderEntity.findById(id);
        SagaEntity state = SagaEntity.findById(id);
        if (order == null || state == null) {
            return RestResponse.notFound();
        }
        return RestResponse.ok(new OrderView(order.id, order.status, state.state, state.deadline, state.version, state.reason));
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
