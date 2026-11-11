package com.patterncatalyst.shipping.notificationservice;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.resteasy.reactive.RestPath;

import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

/** Stands in for the order-service in tests and counts the calls it receives. */
@Singleton
@Path("/")
public class StubOrderService {

    static final AtomicInteger ORDER_CALLS = new AtomicInteger();
    static final AtomicInteger CLAIM_CALLS = new AtomicInteger();

    @GET
    @Path("orders/{id}")
    public String order(@RestPath String id) {
        ORDER_CALLS.incrementAndGet();
        return "{}";
    }

    @GET
    @Path("claims/{id}")
    @Produces("text/plain")
    public String claim(@RestPath String id) {
        CLAIM_CALLS.incrementAndGet();
        return "attachment-for-" + id;
    }
}
