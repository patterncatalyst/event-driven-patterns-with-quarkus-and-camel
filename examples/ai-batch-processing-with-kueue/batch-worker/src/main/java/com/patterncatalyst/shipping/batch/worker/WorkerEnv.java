package com.patterncatalyst.shipping.batch.worker;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

/** The environment of the pod: the one place the commands read RUN_ID, JOB_COMPLETION_INDEX and the fault switches from. */
@ApplicationScoped
public class WorkerEnv {

    public Map<String, String> all() {
        return System.getenv();
    }

    public String get(String name) {
        return all().get(name);
    }

    public String require(String name) {
        String value = get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
