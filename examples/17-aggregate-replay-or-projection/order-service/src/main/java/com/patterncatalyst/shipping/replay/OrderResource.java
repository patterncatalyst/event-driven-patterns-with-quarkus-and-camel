package com.patterncatalyst.shipping.replay;

import java.util.Map;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.Carrier;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** The commands, one endpoint each. A refused command or a lost race is 409. */
@Path("/orders/{id}")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderCommands commands;

    @POST
    public Response place(@PathParam("id") String id) {
        return created(commands.handle(new Command.PlaceOrder(id)));
    }

    @POST
    @Path("/payment")
    public Response pay(@PathParam("id") String id, @QueryParam("paymentId") String paymentId) {
        return created(commands.handle(new Command.PayOrder(id, paymentId == null ? "P-" + id : paymentId)));
    }

    @POST
    @Path("/shipment")
    public Response ship(@PathParam("id") String id, @QueryParam("carrier") Carrier carrier) {
        return created(commands.handle(new Command.ShipOrder(id, carrier)));
    }

    @POST
    @Path("/cancellation")
    public Response cancel(@PathParam("id") String id, @QueryParam("reason") String reason) {
        return created(commands.handle(new Command.CancelOrder(id, reason == null ? "cancelled" : reason)));
    }

    private static Response created(int version) {
        return Response.status(Response.Status.CREATED).entity(Map.of("version", version)).build();
    }

    @ServerExceptionMapper
    Response refused(CommandRefused e) {
        return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
    }

    @ServerExceptionMapper
    Response conflict(ConcurrencyConflict e) {
        return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
    }
}
