package com.patterncatalyst.shipping.batch.runner;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;

/** The step metrics, exported from the long-lived runner because the worker pods live seconds. */
@ApplicationScoped
public class BatchMetrics {

    private final MeterRegistry registry;

    BatchMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void duration(String step, Duration d) {
        seconds("batch.step.duration", step, "Time from the first admission of the step's Job to its end", d);
    }

    void queueWait(String step, Duration d) {
        seconds("batch.step.queue.wait", step, "Time from the step's Job being created to its admission", d);
    }

    void failure(String step) {
        Counter.builder("batch.step.failures").tag("step", step).register(registry).increment();
    }

    void replay() {
        Counter.builder("batch.step.replays").register(registry).increment();
    }

    void preemption(String step) {
        Counter.builder("batch.step.preemptions").tag("step", step).register(registry).increment();
    }

    void partitionRecords(long records) {
        Counter.builder("batch.partition.records").register(registry).increment(records);
    }

    private void seconds(String name, String step, String description, Duration d) {
        DistributionSummary.builder(name).baseUnit("s").description(description).tag("step", step)
                .serviceLevelObjectives(1, 5, 15, 30, 60, 120, 300).register(registry)
                .record(Math.max(0, d.toMillis()) / 1000.0);
    }
}
