package com.patterncatalyst.shipping.aggregate;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * The entry point for commands that the server may retry. A command that loses a version race is decided again
 * from the new stream: the retry wraps a call to the separate transactional bean, so each attempt is a new
 * transaction that reloads. A refusal is not retried.
 */
@ApplicationScoped
@Named("orderCommands")
public class OrderCommands {

    @Inject
    OrderTransaction transaction;

    @Inject
    DemoReappend reappend;

    @ConfigProperty(name = "retry.mode", defaultValue = "redecide")
    String retryMode;

    @Retry(retryOn = ConcurrencyConflict.class, maxRetries = 3, delay = 50, jitter = 25)
    public OrderTransaction.Result handle(Command command) {
        if ("reappend".equals(retryMode)) {
            return reappend.handle(command);
        }
        return transaction.handle(command);
    }
}
