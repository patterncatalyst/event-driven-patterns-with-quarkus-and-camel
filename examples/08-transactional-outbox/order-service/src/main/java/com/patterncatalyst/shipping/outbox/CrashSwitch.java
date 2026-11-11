package com.patterncatalyst.shipping.outbox;

import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, not part of the pattern. For the order ids named in demo.crash-after-commit-for (env DEMO_CRASH_AFTER_COMMIT_FOR) it stops the JVM at once,
 * as a power cut would, after the order's transaction has committed and before the service does anything else.
 */
@ApplicationScoped
public class CrashSwitch {

    @ConfigProperty(name = "demo.crash-after-commit-for")
    Optional<List<String>> orderIds;

    public void afterCommit(String orderId) {
        if (orderIds.orElse(List.of()).contains(orderId)) {
            crash();
        }
    }

    protected void crash() {
        Runtime.getRuntime().halt(137);
    }
}
