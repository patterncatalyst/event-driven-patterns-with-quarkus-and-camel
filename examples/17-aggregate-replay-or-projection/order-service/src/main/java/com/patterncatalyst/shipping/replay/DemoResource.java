package com.patterncatalyst.shipping.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Demo code for verify.sh: a benchmark taken inside the service and a rebuild of the projection. A real service
 * would expose neither.
 */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

    @Inject
    ReplayReport replay;

    @Inject
    CarrierDailyStore store;

    @Inject
    ProjectionBatch batch;

    /** Alternates a replay and a projection read; the first {@code warm} of each are discarded. */
    @GET
    @Path("/bench")
    public Map<String, Object> bench(@QueryParam("warm") @DefaultValue("3") int warm,
            @QueryParam("runs") @DefaultValue("11") int runs) {
        List<Double> replayMs = new ArrayList<>();
        List<Double> projectionMs = new ArrayList<>();
        long events = 0;
        for (int i = 0; i < warm + runs; i++) {
            ReplayReport.Result result = replay.run();
            events = result.eventsRead();
            long started = System.nanoTime();
            store.rows();
            double projection = (System.nanoTime() - started) / 1e6;
            if (i >= warm) {
                replayMs.add((double) result.millis());
                projectionMs.add(projection);
            }
        }
        return Map.of("events", events, "replayMedianMs", median(replayMs), "replayMinMs", Collections.min(replayMs),
                "replayMaxMs", Collections.max(replayMs), "projectionMedianMs", median(projectionMs));
    }

    /**
     * Drops the projection and rewinds the checkpoint. With {@code wait} the call returns once the checkpoint has
     * reached the newest event, polling the checkpoint row, and reports how long that took.
     */
    @POST
    @Path("/projection/rebuild")
    public Map<String, Object> rebuild(@QueryParam("wait") @DefaultValue("false") boolean wait)
            throws InterruptedException {
        ProjectionBatch.Position target = batch.head();
        long started = System.nanoTime();
        batch.reset();
        if (wait) {
            long deadline = System.nanoTime() + 120_000_000_000L;
            while (!batch.checkpoint().reaches(target)) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("the projection did not reach " + target + " in 120 s");
                }
                Thread.sleep(250);
            }
        }
        return Map.of("millis", (System.nanoTime() - started) / 1_000_000);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
