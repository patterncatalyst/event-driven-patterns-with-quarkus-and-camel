package com.patterncatalyst.shipping.batch.worker;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/** The environment of a pod, set by the test. */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestEnv extends WorkerEnv {

    static final Map<String, String> VALUES = new HashMap<>();

    @Override
    public Map<String, String> all() {
        return VALUES;
    }
}
