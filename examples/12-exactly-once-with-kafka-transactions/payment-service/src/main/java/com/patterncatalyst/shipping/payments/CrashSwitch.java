package com.patterncatalyst.shipping.payments;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, not part of the pattern. For the order id named in demo.crash-mid-batch-at (env DEMO_CRASH_MID_BATCH_AT)
 * it stops the JVM at once, as a power cut would, while the transaction holding the records before it is still open.
 * The short wait lets the producer deliver those records to the broker first; a halt with nothing sent yet leaves
 * nothing to show.
 */
@ApplicationScoped
public class CrashSwitch {

    static final Duration FLUSH_WAIT = Duration.ofMillis(500);

    @ConfigProperty(name = "demo.crash-mid-batch-at")
    Optional<String> orderId;

    public boolean armedFor(String id) {
        return orderId.filter(configured -> configured.equals(id)).isPresent();
    }

    /** Completes never: the JVM is gone first. */
    public Uni<Void> halt() {
        return Uni.createFrom().voidItem().onItem().delayIt().by(FLUSH_WAIT)
                .invoke(() -> Runtime.getRuntime().halt(137));
    }
}
