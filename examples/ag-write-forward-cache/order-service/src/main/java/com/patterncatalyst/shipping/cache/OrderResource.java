package com.patterncatalyst.shipping.cache;

import java.net.URI;

import org.jboss.resteasy.reactive.RestResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.patterncatalyst.shipping.Order;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/orders")
public class OrderResource {

    private final OrderService service;
    private final OrderCache cache;

    OrderResource(OrderService service, OrderCache cache) {
        this.service = service;
        this.cache = cache;
    }

    @POST
    public RestResponse<OrderView> place(Order order) throws Exception {
        return RestResponse.ResponseBuilder.<OrderView>created(URI.create("/orders/" + order.id())).entity(service.place(order)).build();
    }

    @POST
    @Path("{id}/cancel")
    public OrderView cancel(@PathParam("id") String id) {
        return service.cancel(id);
    }

    /** Reads Redis and nothing else: a cache miss is a 404, never a fallback to the database. */
    @GET
    @Path("{id}")
    public OrderView get(@PathParam("id") String id) throws JsonProcessingException {
        return cache.find(id).orElseThrow(NotFoundException::new);
    }

    /** The source of truth, for comparison with the cache. */
    @GET
    @Path("{id}/db")
    public OrderView fromDatabase(@PathParam("id") String id) {
        return service.fromDatabase(id);
    }
}
