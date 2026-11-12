package com.patterncatalyst.shipping.aggregate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.OptionalInt;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.OrderStatus;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    public record PlaceRequest(String id, String customerId, String itemSku, int quantity, BigDecimal amount) {
    }

    public record OrderView(String id, OrderStatus status, int version) {
    }

    @Inject
    OrderCommands commands;

    @Inject
    OrderTransaction transaction;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response place(PlaceRequest request) {
        var result = commands.handle(new Command.Place(request.id(), request.customerId(), request.itemSku(),
                request.quantity(), request.amount()));
        return Response.status(Response.Status.CREATED).tag(tag(result.version())).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        OrderAggregate order = transaction.load(id);
        if (order.version() == 0) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(new OrderView(id, order.status(), order.version())).tag(tag(order.version())).build();
    }

    /**
     * Without If-Match the server decides again when it loses a race. With If-Match the caller decided from the
     * version it read: a different current version, or a race while the command runs, answers 412.
     */
    @POST
    @Path("/{id}/cancellation")
    public Response cancel(@PathParam("id") String id, @Context Request request,
            @HeaderParam("If-Match") String ifMatch) {
        var command = new Command.Cancel(id, "customer");
        if (ifMatch == null) {
            return Response.ok().tag(tag(commands.handle(command).version())).build();
        }
        Response.ResponseBuilder stale = request.evaluatePreconditions(tag(transaction.load(id).version()));
        if (stale != null) {
            return stale.build();
        }
        try {
            var result = transaction.handle(command, OptionalInt.of(Integer.parseInt(ifMatch.replace("\"", ""))));
            return Response.ok().tag(tag(result.version())).build();
        } catch (ConcurrencyConflict conflict) {
            return Response.status(Response.Status.PRECONDITION_FAILED).entity(reason(conflict)).build();
        }
    }

    @ServerExceptionMapper
    public Response refused(CommandRefused refused) {
        return Response.status(Response.Status.CONFLICT).entity(reason(refused)).build();
    }

    @ServerExceptionMapper
    public Response conflict(ConcurrencyConflict conflict) {
        return Response.status(Response.Status.CONFLICT).entity(reason(conflict)).build();
    }

    private static EntityTag tag(int version) {
        return new EntityTag(String.valueOf(version));
    }

    private static Map<String, String> reason(RuntimeException failure) {
        return Map.of("reason", failure.getMessage());
    }
}
