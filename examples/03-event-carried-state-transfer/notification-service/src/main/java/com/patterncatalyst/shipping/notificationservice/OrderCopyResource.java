package com.patterncatalyst.shipping.notificationservice;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/order-copies")
public class OrderCopyResource {

    private final OrderCopies copies;
    private final OrderClaims claims;

    OrderCopyResource(OrderCopies copies, @RestClient OrderClaims claims) {
        this.copies = copies;
        this.claims = claims;
    }

    /** Answered from this service's own table. */
    @GET
    @Path("{id}")
    public OrderCopyView get(@RestPath String id) {
        return OrderCopyView.of(copies.find(id));
    }

    /** The one call that leaves this service: fetch the attachment by its claim id. */
    @GET
    @Path("{id}/attachment")
    @Produces(MediaType.TEXT_PLAIN)
    public String attachment(@RestPath String id) {
        OrderCopyView copy = OrderCopyView.of(copies.find(id));
        if (copy.attachment() == null) {
            throw new NotFoundException("order " + id + " has no attachment");
        }
        return claims.content(copy.attachment().id());
    }

    @ServerExceptionMapper
    RestResponse<String> sourceUnavailable(ProcessingException e) {
        return RestResponse.status(RestResponse.Status.SERVICE_UNAVAILABLE, "order-service unreachable");
    }
}
