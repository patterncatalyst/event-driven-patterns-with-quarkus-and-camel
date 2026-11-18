package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWith;

/** Rewrites the runner's routes for a test: the tick is driven by hand and the Jobs live in a FakeCluster. */
final class ClusterRoutes {

    private ClusterRoutes() {
    }

    /** Advises the routes once, whatever the lifecycle of the context between test methods, and starts the context. */
    static void advise(CamelContext context, FakeCluster cluster) throws Exception {
        var tick = context.getRoute("reconcile-tick");
        if (tick != null && tick.getEndpoint().getEndpointUri().startsWith("direct:")) {
            if (!context.getStatus().isStarted()) {
                context.start();
            }
            return;
        }
        AdviceWith.adviceWith(context, "reconcile-tick", route -> route.replaceFromWith("direct:tick"));
        AdviceWith.adviceWith(context, "poll-step",
                route -> route.weaveByToUri("kubernetes-job:*").replace().process(cluster::get));
        AdviceWith.adviceWith(context, "create-job",
                route -> route.weaveByToUri("kubernetes-job:*").replace().process(cluster::create));
        context.start();
    }
}
