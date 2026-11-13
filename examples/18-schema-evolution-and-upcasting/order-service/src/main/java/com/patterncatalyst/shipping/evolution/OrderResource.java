package com.patterncatalyst.shipping.evolution;

import java.util.Map;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.OrderStatus;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

    /** The order as the current code sees it; schemaVersion is the shape OrderPlaced was stored in. */
    public record OrderView(String id, OrderStatus status, String shippingPriority, int schemaVersion, int version) {
    }

    @Inject
    OrderCommands commands;

    @Inject
    EventStore store;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response place(OrderCommands.PlaceRequest request) {
        commands.place(request);
        return Response.status(Response.Status.CREATED).build();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public Response get(@PathParam("id") String id) {
        var stream = store.load(id);
        if (stream.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        OrderAggregate order = OrderAggregate.rehydrate(stream);
        return Response.ok(new OrderView(id, order.status(), order.shippingPriority(), stream.getFirst().storedSchemaVersion(),
                order.version())).build();
    }

    @POST
    @Path("/{id}/cancellation")
    public Response cancel(@PathParam("id") String id) {
        commands.cancel(id);
        return Response.ok().build();
    }

    @ServerExceptionMapper
    public Response refused(CommandRefused refused) {
        return Response.status(Response.Status.CONFLICT).entity(Map.of("reason", refused.getMessage())).build();
    }

    /** The registry said no: the event was not stored and nothing was published. */
    @ServerExceptionMapper
    public Response schemaRefused(SchemaRefused refused) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(Map.of("reason", refused.getMessage())).build();
    }
}
