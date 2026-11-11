package com.patterncatalyst.shipping.eventnotification.notification;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

import com.patterncatalyst.shipping.OrderStatus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Stands in for order-service on the test application's own port. */
@Path("/orders")
public class StubOrderService {

    static final Map<String, FetchedOrder> ORDERS = new ConcurrentHashMap<>();
    static final AtomicBoolean DOWN = new AtomicBoolean();

    static void put(String id, String customerId, String amount, OrderStatus status) {
        ORDERS.put(id, new FetchedOrder(customerId, new BigDecimal(amount), status));
    }

    @GET
    @Path("{id}")
    public RestResponse<FetchedOrder> get(@RestPath String id) {
        FetchedOrder order = ORDERS.get(id);
        if (DOWN.get()) {
            return RestResponse.status(RestResponse.Status.SERVICE_UNAVAILABLE);
        }
        return order == null ? RestResponse.notFound() : RestResponse.ok(order);
    }
}
