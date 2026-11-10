package com.patterncatalyst.shipping.crudvsevents;

import java.net.URI;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.Order;

import jakarta.ws.rs.GET;
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

    @POST
    @Path("{id}/status")
    public RestResponse<Void> changeStatus(@RestPath String id, StatusRequest request) {
        service.change(new ChangeOrderStatus(id, request.to(), request.expectedVersion(), request.reason()));
        return RestResponse.noContent();
    }

    /** The Order as the row holds it. */
    @GET
    @Path("{id}")
    public OrderDocument fromRow(@RestPath String id) {
        return service.fromRow(id);
    }

    /** The same document rebuilt by replaying the event stream. */
    @GET
    @Path("{id}/replayed")
    public OrderDocument fromEvents(@RestPath String id) {
        return service.fromEvents(id);
    }

    @GET
    @Path("{id}/history")
    public StatusHistory history(@RestPath String id) {
        return service.history(id);
    }

    @ServerExceptionMapper
    RestResponse<String> illegal(IllegalTransitionException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> stale(ConcurrentAppendException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> duplicate(DuplicateOrderException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> missing(OrderNotFoundException e) {
        return RestResponse.status(RestResponse.Status.NOT_FOUND, e.getMessage());
    }
}
