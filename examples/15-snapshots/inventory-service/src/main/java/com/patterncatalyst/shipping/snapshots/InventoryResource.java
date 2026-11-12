package com.patterncatalyst.shipping.snapshots;

import java.util.Map;

import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/items/{sku}")
@Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {

    public record Reserve(String orderId, int quantity) {
    }

    public record Receive(int quantity) {
    }

    @Inject
    StockLedger ledger;

    @Inject
    ReservationCommands commands;

    /** The state and what loading it cost. ignoreSnapshot=true is a demo switch: the same read from the first event. */
    @GET
    public Map<String, Object> get(@PathParam("sku") String sku, @QueryParam("ignoreSnapshot") boolean ignoreSnapshot) {
        var loaded = ledger.load(sku, !ignoreSnapshot);
        var stats = loaded.stats();
        return Map.of(
                "sku", sku,
                "quantityOnHand", loaded.aggregate().quantityOnHand(),
                "version", loaded.aggregate().version(),
                "source", stats.source(),
                "snapshotVersion", stats.snapshotVersion(),
                "eventsReplayed", stats.eventsReplayed(),
                "loadNanos", stats.loadNanos());
    }

    @POST
    @Path("/reservations")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestResponse<ReservationTransaction.Result> reserve(@PathParam("sku") String sku, Reserve request) {
        return RestResponse.status(RestResponse.Status.CREATED, commands.reserve(sku, request.orderId(), request.quantity()));
    }

    @POST
    @Path("/receipts")
    @Consumes(MediaType.APPLICATION_JSON)
    public RestResponse<ReservationTransaction.Result> receive(@PathParam("sku") String sku, Receive request) {
        return RestResponse.status(RestResponse.Status.CREATED, commands.receive(sku, request.quantity()));
    }

    /** A refused command is a business outcome, not a failure. */
    @ServerExceptionMapper
    RestResponse<Map<String, String>> outOfStock(OutOfStock refused) {
        return RestResponse.status(RestResponse.Status.CONFLICT, Map.of("reason", refused.getMessage()));
    }
}
