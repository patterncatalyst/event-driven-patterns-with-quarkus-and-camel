package com.patterncatalyst.shipping.projections;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.patterncatalyst.shipping.OrderStatus;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The query side. It answers from the view only and never touches Kafka; {@code asOf} says how current the answer is. */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class OrderViewResource {

    public record Summary(String orderId, String customerId, BigDecimal amount, OrderStatus status,
            String shipmentStatus, String carrier, String trackingNumber, Instant asOf) {

        static Summary of(OrderView view) {
            return new Summary(view.orderId, view.customerId, view.amount, view.status(), view.shipmentStatus, view.carrier,
                    view.trackingNumber, view.lastEventAt);
        }
    }

    @GET
    @Path("/orders/{id}")
    public Summary order(@PathParam("id") String id) {
        OrderView view = OrderView.findById(id);
        if (view == null) {
            throw new NotFoundException();
        }
        return Summary.of(view);
    }

    @GET
    @Path("/customers/{customerId}/orders")
    public List<Summary> orders(@PathParam("customerId") String customerId) {
        return OrderView.<OrderView>list("customerId = ?1 order by orderId", customerId).stream().map(Summary::of).toList();
    }
}
