package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

/** The controller loop: every tick, look at the step of each running run that has work. */
@ApplicationScoped
public class ReconcileTickRoute extends RouteBuilder {

    @Override
    public void configure() {
        // camel-kubernetes has no kubernetes-job consumer, so the Jobs are read on a timer, as a controller does.
        from("scheduler:reconcile?delay={{reconcile.tick}}&initialDelay=2000").routeId("reconcile-tick")
                .bean("stepService", "workQueue")
                .split(body()).to("direct:poll-step");
    }
}
