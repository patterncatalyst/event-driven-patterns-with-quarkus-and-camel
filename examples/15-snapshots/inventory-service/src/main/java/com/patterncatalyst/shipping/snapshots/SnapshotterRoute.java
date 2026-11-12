package com.patterncatalyst.shipping.snapshots;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Greg Young's out-of-band snapshotter as a route: a timer, a query for the streams that are too far past their last
 * snapshot, and one call per stream. Off unless inventory.snapshotter.enabled is true (profile camel-snapshotter).
 */
@ApplicationScoped
public class SnapshotterRoute extends RouteBuilder {

    private static final String TOO_FAR_BEHIND =
            "select h.stream_id as stream_id "
                    + "from (select stream_id, max(version) as v from inventory.events group by stream_id) h "
                    + "left join inventory.snapshots s on s.stream_id = h.stream_id "
                    + "where h.v - coalesce(s.version, 0) >= :#n";

    @Inject
    SnapshotConfig config;

    @Override
    public void configure() {
        from("scheduler:snapshotter?delay={{inventory.snapshotter.delay-ms}}")
                .routeId("snapshotter")
                .autoStartup("{{inventory.snapshotter.enabled}}")
                .setHeader("n", constant(config.snapshot().every()))
                .to("sql:" + TOO_FAR_BEHIND)
                .split(body())
                .setBody(simple("${body[stream_id]}"))
                .bean("snapshots", "take");
    }
}
