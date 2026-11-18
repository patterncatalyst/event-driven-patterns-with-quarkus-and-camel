package com.patterncatalyst.shipping.batch.runner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.kubernetes.KubernetesConstants;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;

/** The Jobs of a cluster that exists only in memory, standing in for the kubernetes-job endpoint. */
final class FakeCluster {

    final Map<String, Job> jobs = new LinkedHashMap<>();

    void reset() {
        jobs.clear();
    }

    void create(Exchange exchange) {
        var in = exchange.getMessage();
        String name = in.getHeader(KubernetesConstants.KUBERNETES_JOB_NAME, String.class);
        @SuppressWarnings("unchecked")
        Map<String, String> labels = in.getHeader(KubernetesConstants.KUBERNETES_JOB_LABELS, Map.class);
        JobSpec spec = in.getHeader(KubernetesConstants.KUBERNETES_JOB_SPEC, JobSpec.class);
        if (jobs.containsKey(name)) {
            throw new IllegalStateException("Job " + name + " already exists");
        }
        jobs.put(name, new JobBuilder().withNewMetadata().withName(name).withLabels(labels).endMetadata()
                .withSpec(spec).build());
        in.setBody(jobs.get(name));
    }

    void get(Exchange exchange) {
        String name = exchange.getMessage().getHeader(KubernetesConstants.KUBERNETES_JOB_NAME, String.class);
        exchange.getMessage().setBody(jobs.get(name));
    }

    Job job(String name) {
        return jobs.get(name);
    }

    List<String> names() {
        return new ArrayList<>(jobs.keySet());
    }

    /** What Kueue does on admission, and on preemption. */
    void suspend(String name, boolean suspend) {
        jobs.get(name).getSpec().setSuspend(suspend);
    }

    void finish(String name, String type) {
        jobs.put(name, new JobBuilder(jobs.get(name)).withNewStatus().addNewCondition().withType(type)
                .withStatus("True").endCondition().endStatus().build());
    }
}
