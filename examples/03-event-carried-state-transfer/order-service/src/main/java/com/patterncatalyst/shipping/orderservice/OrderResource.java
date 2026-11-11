package com.patterncatalyst.shipping.orderservice;

import java.net.URI;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/orders")
public class OrderResource {

    private final OrderService service;
    private final OrderPublisher publisher;

    OrderResource(OrderService service, OrderPublisher publisher) {
        this.service = service;
        this.publisher = publisher;
    }

    @POST
    public RestResponse<Void> place(PlaceOrderRequest request) {
        Stored stored = service.place(request);
        publisher.placed(stored);
        return RestResponse.created(URI.create("/orders/" + stored.order().id()));
    }

    @POST
    @Path("{id}/cancel")
    public RestResponse<Void> cancel(@RestPath String id) {
        publisher.cancelled(service.cancel(id));
        return RestResponse.noContent();
    }

    @GET
    @Path("{id}")
    public OrderDocument get(@RestPath String id) {
        return service.find(id);
    }

    @ServerExceptionMapper
    RestResponse<String> invalid(IllegalArgumentException e) {
        return RestResponse.status(RestResponse.Status.BAD_REQUEST, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> rejected(EventRejectedException e) {
        return RestResponse.status(RestResponse.Status.BAD_GATEWAY, e.getMessage());
    }
}
