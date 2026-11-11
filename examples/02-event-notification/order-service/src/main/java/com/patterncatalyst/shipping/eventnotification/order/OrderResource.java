package com.patterncatalyst.shipping.eventnotification.order;

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

    @GET
    @Path("{id}")
    public OrderView get(@RestPath String id) {
        return service.get(id);
    }

    @POST
    @Path("{id}/cancel")
    public RestResponse<Void> cancel(@RestPath String id) {
        service.cancel(id);
        return RestResponse.noContent();
    }

    @ServerExceptionMapper
    RestResponse<String> duplicate(DuplicateOrderException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> illegal(IllegalTransitionException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> missing(OrderNotFoundException e) {
        return RestResponse.status(RestResponse.Status.NOT_FOUND, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> invalid(IllegalArgumentException e) {
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, e.getMessage());
    }
}
