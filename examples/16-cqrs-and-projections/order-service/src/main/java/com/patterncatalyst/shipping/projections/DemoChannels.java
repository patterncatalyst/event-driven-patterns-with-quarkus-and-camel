package com.patterncatalyst.shipping.projections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Demo code. Pauses and resumes one channel so a topic can be held back while the others advance, and reports the
 * lag percentiles the service measured. A real projector exposes the lag as a metric and nothing else.
 */
@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
public class DemoChannels {

    static final List<String> CHANNELS = List.of("placed", "cancelled", "paid", "scheduled", "in-transit", "delivered");

    @Inject
    ChannelRegistry channels;

    @Inject
    MeterRegistry registry;

    @GET
    @Path("/channels")
    public Map<String, Boolean> state() {
        Map<String, Boolean> paused = new LinkedHashMap<>();
        CHANNELS.forEach(name -> paused.put(name, channels.getPausable(name).isPaused()));
        return paused;
    }

    @GET
    @Path("/channels/{name}")
    public Map<String, Boolean> state(@PathParam("name") String name) {
        return Map.of("paused", channels.getPausable(name).isPaused());
    }

    @POST
    @Path("/channels/{name}/pause")
    public Map<String, Boolean> pause(@PathParam("name") String name) {
        PausableChannel channel = channels.getPausable(name);
        channel.pause();
        return Map.of("paused", channel.isPaused());
    }

    @POST
    @Path("/channels/{name}/resume")
    public Map<String, Boolean> resume(@PathParam("name") String name) {
        PausableChannel channel = channels.getPausable(name);
        channel.resume();
        return Map.of("paused", channel.isPaused());
    }

    @GET
    @Path("/lag")
    public Map<String, Object> lag() {
        Timer timer = registry.find("order.view.lag").timer();
        Map<String, Object> out = new LinkedHashMap<>();
        if (timer == null) {
            out.put("count", 0);
            return out;
        }
        out.put("count", timer.count());
        for (var value : timer.takeSnapshot().percentileValues()) {
            out.put("p" + Math.round(value.percentile() * 100) + "Ms", Math.round(value.value(TimeUnit.MILLISECONDS)));
        }
        out.put("maxMs", Math.round(timer.max(TimeUnit.MILLISECONDS)));
        return out;
    }
}
