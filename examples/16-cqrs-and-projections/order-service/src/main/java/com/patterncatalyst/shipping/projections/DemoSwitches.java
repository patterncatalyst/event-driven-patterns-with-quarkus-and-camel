package com.patterncatalyst.shipping.projections;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, both switches off by default. PROJECTION_GUARD=off removes the forward-only guard from the shipment
 * upsert, so the last event applied wins. PROJECTOR_DELAY_MS makes every event take that long to apply. Nothing in a
 * real projector does either.
 */
@ApplicationScoped
public class DemoSwitches {

    @ConfigProperty(name = "projection.guard", defaultValue = "on")
    String guard;

    @ConfigProperty(name = "projection.delay-ms", defaultValue = "0")
    long delayMs;

    public boolean guardOn() {
        return !"off".equals(guard);
    }

    public void delay() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
