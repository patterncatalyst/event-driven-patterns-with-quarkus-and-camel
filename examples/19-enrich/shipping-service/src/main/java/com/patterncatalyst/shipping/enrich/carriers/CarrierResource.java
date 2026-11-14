package com.patterncatalyst.shipping.enrich.carriers;

import com.patterncatalyst.shipping.Carrier;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The lookup the Content Enricher calls for every shipment. The PUT is demo code: it stands for an operator changing a
 * carrier's tracking page. Publishing the change to the compacted topic is the owner's job in a real service, through
 * the outbox of chapter 8 or the log tailing of chapter 9; the example's verify.sh publishes it instead.
 */
@Path("/carriers/{carrier}")
@Produces(MediaType.APPLICATION_JSON)
public class CarrierResource {

    /** The wire form of a carrier's reference data. */
    public record CarrierReference(Carrier carrier, String mode, String trackingUrlTemplate) {

        static CarrierReference of(CarrierRecord row) {
            return new CarrierReference(Carrier.valueOf(row.carrier), row.mode, row.trackingUrlTemplate);
        }
    }

    public record TemplateChange(String trackingUrlTemplate) {
    }

    @GET
    public CarrierReference get(@PathParam("carrier") String carrier) {
        CarrierRecord row = CarrierRecord.findById(carrier);
        if (row == null) {
            throw new NotFoundException();
        }
        return CarrierReference.of(row);
    }

    /** Demo code: changes the tracking URL template of one carrier. */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public CarrierReference changeTemplate(@PathParam("carrier") String carrier, TemplateChange change) {
        CarrierRecord row = CarrierRecord.findById(carrier);
        if (row == null) {
            throw new NotFoundException();
        }
        row.trackingUrlTemplate = change.trackingUrlTemplate();
        return CarrierReference.of(row);
    }
}
