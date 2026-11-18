package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;

import jakarta.enterprise.context.ApplicationScoped;

/** Creates the Job of a step. The Job is created suspended; Kueue admits it. */
@ApplicationScoped
public class CreateJobRoute extends RouteBuilder {

    private final JobFactory jobs;

    CreateJobRoute(JobFactory jobs) {
        this.jobs = jobs;
    }

    @Override
    public void configure() {
        from("direct:create-job").routeId("create-job")
                .bean("stepService", "markQueued")
                .setVariable("step", body())
                .process(jobs::prepare)
                .to("kubernetes-job:local?operation=createJob")
                .log(LoggingLevel.INFO, "created the Job of step ${variable.step}");
    }
}
