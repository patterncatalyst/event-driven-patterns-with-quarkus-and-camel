package com.patterncatalyst.shipping.testing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

import com.patterncatalyst.shipping.OrderStatus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Stands in for order-service on the test application's own port, so a test decides what the fetch answers without a
 * second process. An order the test never registered is PLACED.
 */
@Path("/orders")
public class StubOrderService {

    static final Map<String, OrderStatus> STATUS = new ConcurrentHashMap<>();

    @GET
    @Path("{id}")
    public RestResponse<FetchedOrder> get(@RestPath String id) {
        return RestResponse.ok(new FetchedOrder("C-36", new BigDecimal("59.90"), STATUS.getOrDefault(id, OrderStatus.PLACED)));
    }
}
