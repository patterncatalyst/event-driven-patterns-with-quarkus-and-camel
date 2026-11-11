package com.patterncatalyst.shipping.ordering;

import java.util.List;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.RestPath;

/** Reads what the replicas have applied. */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    public record Status(String orderId, OrderStatus status, String lastEvent) {
    }

    public record Applied(String eventType, int sequence, OrderStatus status, String replica) {
    }

    @GET
    @Path("{id}/status")
    public Status status(@RestPath String id) {
        OrderStatusRow row = OrderStatusRow.findById(id);
        if (row == null) {
            throw new NotFoundException();
        }
        return new Status(row.orderId, row.status, row.lastEvent);
    }

    @GET
    @Path("{id}/history")
    public List<Applied> history(@RestPath String id) {
        return StatusHistoryRow.<StatusHistoryRow>list("orderId", Sort.by("id"), id).stream()
                .map(h -> new Applied(h.eventType, h.sequence, h.status, h.replica))
                .toList();
    }
}
