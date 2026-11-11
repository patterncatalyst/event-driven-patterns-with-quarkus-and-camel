package com.patterncatalyst.shipping.orderservice;

import org.jboss.resteasy.reactive.RestPath;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The claim check's counter: hand over a claim id, get the stored content. */
@Path("/claims")
public class ClaimResource {

    private final OrderService service;

    ClaimResource(OrderService service) {
        this.service = service;
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public String content(@RestPath String id) {
        return service.claimContent(id);
    }
}
