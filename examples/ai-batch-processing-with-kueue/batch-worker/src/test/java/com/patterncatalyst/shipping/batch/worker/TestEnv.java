package com.patterncatalyst.shipping.batch.worker;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/** The settings of a pod, set by the test under the names of the environment variables. */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestEnv implements WorkerEnv {

    static final Map<String, String> VALUES = new HashMap<>();

    private static Optional<String> value(String name) {
        String v = VALUES.get(name);
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.trim());
    }

    private static <T> Optional<T> value(String name, Function<String, T> parse) {
        return value(name).map(parse);
    }

    @Override
    public Optional<String> runId() {
        return value("RUN_ID");
    }

    @Override
    public Optional<Integer> partition() {
        return value("JOB_COMPLETION_INDEX", Integer::valueOf);
    }

    @Override
    public Optional<Integer> indexFailureCount() {
        return value("JOB_INDEX_FAILURE_COUNT", Integer::valueOf);
    }

    @Override
    public Optional<Integer> faultPartition() {
        return value("FAULT_PARTITION", Integer::valueOf);
    }

    @Override
    public Optional<String> faultMode() {
        return value("FAULT_MODE");
    }

    @Override
    public Optional<Integer> slowPartition() {
        return value("SLOW_PARTITION", Integer::valueOf);
    }

    @Override
    public Optional<Long> slowSeconds() {
        return value("SLOW_SECONDS", Long::valueOf);
    }

    @Override
    public Optional<String> replayGuard() {
        return value("REPLAY_GUARD");
    }

    @Override
    public Optional<String> traceparent() {
        return value("TRACEPARENT");
    }
}
