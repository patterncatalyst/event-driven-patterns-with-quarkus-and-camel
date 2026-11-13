package com.patterncatalyst.shipping.replay;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, off unless DEMO_HOLD_FOR is set. Holds a listed command ("O-1701:ShipOrder") once, after its event is
 * appended and before the transaction commits, so a later command can commit first. Nothing in a real service waits
 * here.
 */
@ApplicationScoped
public class DemoHold {

    private static final Logger LOG = Logger.getLogger(DemoHold.class);

    @ConfigProperty(name = "demo.hold-for")
    Optional<List<String>> holdFor;

    @ConfigProperty(name = "demo.hold-ms")
    long holdMs;

    private final Set<String> done = ConcurrentHashMap.newKeySet();

    public void afterAppend(String orderId, String command) {
        String key = orderId + ":" + command;
        if (holdFor.orElse(List.of()).contains(key) && done.add(key)) {
            LOG.infof("held %s %s after append", command, orderId);
            try {
                Thread.sleep(holdMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
