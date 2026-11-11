package com.patterncatalyst.shipping.queues;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * A stand-in for an email provider. The customer id decides what happens:
 * C-SLOW takes the slow time, C-RETRY fails on the first attempt only, C-BOUNCE always fails for good.
 * The attempt number comes from the caller, so any worker can serve any attempt.
 */
@ApplicationScoped
public class EmailGateway {

    private final long baseMs;
    private final long slowMs;

    public EmailGateway(@ConfigProperty(name = "delivery.gateway.base-ms") long baseMs,
                        @ConfigProperty(name = "delivery.gateway.slow-ms") long slowMs) {
        this.baseMs = baseMs;
        this.slowMs = slowMs;
    }

    public void send(String customerId, int attempt) {
        if (customerId.startsWith("C-BOUNCE")) {
            throw GatewayFailure.permanentFailure("mailbox does not exist for " + customerId);
        }
        if (customerId.startsWith("C-RETRY") && attempt == 1) {
            throw GatewayFailure.transientFailure("provider timeout for " + customerId);
        }
        pause(customerId.startsWith("C-SLOW") ? slowMs : baseMs);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
