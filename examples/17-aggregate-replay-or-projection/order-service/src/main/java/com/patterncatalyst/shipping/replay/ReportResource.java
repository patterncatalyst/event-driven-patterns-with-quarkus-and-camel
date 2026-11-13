package com.patterncatalyst.shipping.replay;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** The same question, answered either by replaying every aggregate or by reading the projection. */
@Path("/reports/shipments-per-carrier")
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    /** {@code eventsRead} belongs to a replay and {@code lagEvents} to the projection; the other is absent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Report(String source, List<ShipmentsPerDay> rows, Long eventsRead, Long lagEvents, double millis) {
    }

    @Inject
    ReplayReport replay;

    @Inject
    CarrierDailyStore store;

    @Inject
    ProjectionBatch batch;

    @GET
    public Report report(@QueryParam("source") @DefaultValue("projection") String source) {
        if ("replay".equals(source)) {
            ReplayReport.Result result = replay.run();
            return new Report(source, result.rows(), result.eventsRead(), null, result.millis());
        }
        long started = System.nanoTime();
        List<ShipmentsPerDay> rows = store.rows();
        double millis = (System.nanoTime() - started) / 1e6;
        return new Report("projection", rows, null, batch.lagEvents(), millis);
    }
}
