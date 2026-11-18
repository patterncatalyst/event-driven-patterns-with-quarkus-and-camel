package com.patterncatalyst.shipping.batch.runner;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.KubernetesConstants;

import io.fabric8.kubernetes.api.model.ConfigMapEnvSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.PodFailurePolicyBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import jakarta.enterprise.context.ApplicationScoped;

/** Builds the Job of a step from the packaged template. */
@ApplicationScoped
public class JobFactory {

    static final String QUEUE_LABEL = "kueue.x-k8s.io/queue-name";
    static final String PRIORITY_LABEL = "kueue.x-k8s.io/priority-class";
    static final String RUN_LABEL = "edp.batch/run";
    static final String STEP_LABEL = "edp.batch/step";
    static final int PARTITIONS = 3;
    static final int POISON = 42;

    // Credentials are not copied: the template reads them from the edp-pg-app Secret.
    static final List<String> PASSED_THROUGH = List.of("KAFKA_BOOTSTRAP_SERVERS", "QUARKUS_DATASOURCE_JDBC_URL",
            "QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT");

    private final BatchConfig config;
    private final Job template;
    private final UnaryOperator<String> environment = System::getenv;

    JobFactory(BatchConfig config) {
        this.config = config;
        try (InputStream in = getClass().getResourceAsStream("/job-template.yaml")) {
            this.template = Serialization.unmarshal(in, Job.class);
        } catch (IOException e) {
            throw new IllegalStateException("job-template.yaml", e);
        }
    }

    /** Puts what the kubernetes-job producer needs on the message: namespace, name, labels and spec. */
    public void prepare(Exchange exchange) {
        StepRef ref = exchange.getVariable("step", StepRef.class);
        var in = exchange.getMessage();
        in.setHeader(KubernetesConstants.KUBERNETES_NAMESPACE_NAME, config.namespace());
        in.setHeader(KubernetesConstants.KUBERNETES_JOB_NAME, ref.jobName());
        in.setHeader(KubernetesConstants.KUBERNETES_JOB_LABELS, labels(ref));
        in.setHeader(KubernetesConstants.KUBERNETES_JOB_SPEC, spec(ref));
    }

    Map<String, String> labels(StepRef ref) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(RUN_LABEL, ref.runId());
        labels.put(STEP_LABEL, ref.name());
        if (config.on(config.queue())) {
            labels.put(QUEUE_LABEL, config.queueName());
        }
        if (ref.priority() != null && config.on(config.priority())) {
            labels.put(PRIORITY_LABEL, ref.priority());
        }
        return labels;
    }

    JobSpec spec(StepRef ref) {
        JobSpec base = Serialization.clone(template).getSpec();
        var builder = new JobSpecBuilder(base);
        boolean reconcile = StepRef.RECONCILE.equals(ref.name());
        if (reconcile) {
            builder.withCompletionMode("Indexed").withCompletions(PARTITIONS).withParallelism(PARTITIONS)
                    .withBackoffLimitPerIndex(1).withMaxFailedIndexes(PARTITIONS);
            if (config.on(config.failPolicy())) {
                builder.withPodFailurePolicy(new PodFailurePolicyBuilder()
                        .addNewRule().withAction("FailIndex").withNewOnExitCodes().withContainerName("worker")
                        .withOperator("In").withValues(POISON).endOnExitCodes().endRule()
                        .addNewRule().withAction("Ignore").addNewOnPodCondition().withType("DisruptionTarget")
                        .withStatus("True").endOnPodCondition().endRule().build());
            }
        } else {
            builder.withCompletions(1).withParallelism(1).withBackoffLimit(0);
        }
        builder.editTemplate().withNewMetadata().withLabels(Map.of(RUN_LABEL, ref.runId(), STEP_LABEL, ref.name()))
                .endMetadata()
                .editSpec().editFirstContainer().withImage(config.workerImage()).withArgs(ref.name())
                .withEnv(env(ref, base))
                .withEnvFrom(new EnvFromSourceBuilder().withConfigMapRef(
                        new ConfigMapEnvSourceBuilder().withName(config.faultsConfigMap()).withOptional(true).build())
                        .build())
                .withResources(new ResourceRequirementsBuilder()
                        .addToRequests("cpu", new Quantity(reconcile ? "500m" : "250m"))
                        .addToRequests("memory", new Quantity(reconcile ? "384Mi" : "256Mi"))
                        .addToLimits("memory", new Quantity(reconcile ? "512Mi" : "384Mi")).build())
                .endContainer().endSpec().endTemplate();
        return builder.build();
    }

    private List<EnvVar> env(StepRef ref, JobSpec base) {
        var env = new java.util.ArrayList<>(base.getTemplate().getSpec().getContainers().get(0).getEnv());
        env.add(new EnvVarBuilder().withName("RUN_ID").withValue(ref.runId()).build());
        env.add(new EnvVarBuilder().withName("STEP").withValue(ref.name()).build());
        env.add(new EnvVarBuilder().withName("REPLAY_GUARD").withValue(config.replayGuard()).build());
        // The pods reach Kafka, the database and the collector with the settings the runner itself runs with.
        for (String name : PASSED_THROUGH) {
            String value = environment.apply(name);
            if (value != null && !value.isBlank()) {
                env.add(new EnvVarBuilder().withName(name).withValue(value).build());
            }
        }
        if (ref.traceparent() != null) {
            env.add(new EnvVarBuilder().withName("TRACEPARENT").withValue(ref.traceparent()).build());
        }
        return env;
    }
}
