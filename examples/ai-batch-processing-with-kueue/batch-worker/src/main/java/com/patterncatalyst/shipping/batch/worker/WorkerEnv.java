package com.patterncatalyst.shipping.batch.worker;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

/**
 * The settings of the pod. Config maps each environment variable onto the dotted name (RUN_ID is run.id), so the Job
 * template sets plain environment variables and the code reads typed values. A blank variable counts as unset.
 */
@ConfigMapping(prefix = "")
public interface WorkerEnv {

    @WithName("run.id")
    Optional<String> runId();

    @WithName("job.completion.index")
    Optional<Integer> partition();

    @WithName("job.index.failure.count")
    Optional<Integer> indexFailureCount();

    @WithName("fault.partition")
    Optional<Integer> faultPartition();

    @WithName("fault.mode")
    Optional<String> faultMode();

    @WithName("slow.partition")
    Optional<Integer> slowPartition();

    @WithName("slow.seconds")
    Optional<Long> slowSeconds();

    @WithName("replay.guard")
    Optional<String> replayGuard();

    /** The W3C trace context of the run's span, from the TRACEPARENT variable. */
    @WithName("traceparent")
    Optional<String> traceparent();

    default String requireRunId() {
        return runId().orElseThrow(() -> new IllegalStateException("RUN_ID is not set"));
    }

    default int requirePartition() {
        return partition().orElseThrow(() -> new IllegalStateException("JOB_COMPLETION_INDEX is not set"));
    }
}
