package com.patterncatalyst.shipping.crudvsevents;

import java.net.URI;

import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.patterncatalyst.shipping.Consignment;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/consignments")
public class ConsignmentResource {

    private final ConsignmentService service;

    ConsignmentResource(ConsignmentService service) {
        this.service = service;
    }

    @POST
    public RestResponse<Void> book(Consignment consignment) {
        service.book(consignment);
        return RestResponse.created(URI.create("/consignments/" + consignment.id()));
    }

    @POST
    @Path("{id}/status")
    public RestResponse<Void> changeStatus(@RestPath String id, StatusRequest request) {
        service.change(new ChangeConsignmentStatus(id, request.to(), request.expectedVersion()));
        return RestResponse.noContent();
    }

    /** The Consignment as the row holds it. */
    @GET
    @Path("{id}")
    public ConsignmentDocument fromRow(@RestPath String id) {
        return service.fromRow(id);
    }

    /** The same document rebuilt by replaying the event stream. */
    @GET
    @Path("{id}/replayed")
    public ConsignmentDocument fromEvents(@RestPath String id) {
        return service.fromEvents(id);
    }

    @GET
    @Path("{id}/history")
    public StatusHistory history(@RestPath String id) {
        return service.history(id);
    }

    @ServerExceptionMapper
    RestResponse<String> illegal(IllegalTransitionException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> stale(ConcurrentAppendException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> duplicate(DuplicateConsignmentException e) {
        return RestResponse.status(RestResponse.Status.CONFLICT, e.getMessage());
    }

    @ServerExceptionMapper
    RestResponse<String> missing(ConsignmentNotFoundException e) {
        return RestResponse.status(RestResponse.Status.NOT_FOUND, e.getMessage());
    }
}
