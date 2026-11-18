package com.patterncatalyst.shipping.batch.runner;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesConstants;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import jakarta.enterprise.context.ApplicationScoped;

/** Reads a step's Job and moves the step to the state the Job is in. */
@ApplicationScoped
public class PollStepRoute extends RouteBuilder {

    private final BatchConfig config;

    PollStepRoute(BatchConfig config) {
        this.config = config;
    }

    @Override
    public void configure() {
        from("direct:poll-step").routeId("poll-step")
                .onException(Exception.class).handled(true)
                    .log(LoggingLevel.WARN, "step ${variable.step} not advanced: ${exception.message}")
                .end()
                .setVariable("step", body())
                .process(e -> {
                    StepRef ref = e.getVariable("step", StepRef.class);
                    e.getMessage().setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, config.namespace());
                    e.getMessage().setHeader(KubernetesConstants.KUBERNETES_JOB_NAME, ref.jobName());
                })
                .to("kubernetes-job:local?operation=getJob")
                .process(e -> {
                    JobState state = JobState.of(e.getMessage().getBody(Job.class));
                    // A Job that was admitted and is now gone was deleted by someone; creating it again would hide that.
                    boolean admitted = e.getVariable("step", StepRef.class).running();
                    e.setVariable("jobState", state == JobState.MISSING && admitted ? JobState.LOST : state);
                })
                .setBody(variable("step"))
                .choice()
                    .when(variable("jobState").isEqualTo(JobState.MISSING))
                        .to("direct:create-job")
                    .when(variable("jobState").isEqualTo(JobState.LOST))
                        .bean("stepService", "failed(${body}, 'the Job is gone')")
                    .when(variable("jobState").isEqualTo(JobState.SUSPENDED))
                        .bean("stepService", "preempted")
                    .when(variable("jobState").isEqualTo(JobState.ACTIVE))
                        .bean("stepService", "admitted")
                    .when(variable("jobState").isEqualTo(JobState.COMPLETE))
                        .bean("stepService", "completed")
                    .when(variable("jobState").isEqualTo(JobState.FAILED))
                        .bean("stepService", "failed(${body}, 'the Job failed')")
                .end();
    }
}
