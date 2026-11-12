package com.patterncatalyst.shipping.inbox;

import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, not part of the pattern. For the order ids named in demo.fail-after-effect-for (env
 * DEMO_FAIL_AFTER_EFFECT_FOR) the reservation fails after it has decremented the stock, so the effect has to
 * roll back.
 */
@ApplicationScoped
public class DemoFailure {

    @ConfigProperty(name = "demo.fail-after-effect-for")
    Optional<List<String>> orderIds;

    public void afterEffect(String orderId) {
        if (orderIds.orElse(List.of()).contains(orderId)) {
            throw new IllegalStateException("demo failure after the effect for " + orderId);
        }
    }
}
