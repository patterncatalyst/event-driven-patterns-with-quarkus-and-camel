package com.patterncatalyst.shipping.notificationservice;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The order-service's claim counter. Called only when a caller asks for an attachment. */
@RegisterRestClient(configKey = "order-claims")
@Path("/claims")
public interface OrderClaims {

    @GET
    @Path("{id}")
    @Produces(MediaType.TEXT_PLAIN)
    String content(@RestPath String id);
}
