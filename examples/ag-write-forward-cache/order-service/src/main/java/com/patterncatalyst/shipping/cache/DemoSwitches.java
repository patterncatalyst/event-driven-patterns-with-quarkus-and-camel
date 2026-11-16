package com.patterncatalyst.shipping.cache;

import java.util.Arrays;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, not part of the pattern. With {@code CACHE_WRITE=dual} the request writes Redis before the database
 * commit, which is the dual write the pattern avoids. An order id named in {@code FAIL_BEFORE_COMMIT_FOR} then fails
 * the transaction, as a crash or a constraint violation would.
 */
@ApplicationScoped
class DemoSwitches {

    private final OrderCache cache;

    @ConfigProperty(name = "demo.cache-write")
    String cacheWrite;

    @ConfigProperty(name = "demo.fail-before-commit-for")
    String failFor;

    DemoSwitches(OrderCache cache) {
        this.cache = cache;
    }

    void beforeCommit(OrderView view) throws Exception {
        if ("dual".equals(cacheWrite)) {
            cache.apply(view);
        }
        if (Arrays.asList(failFor.split(",")).contains(view.orderId())) {
            throw new IllegalStateException("demo failure before the commit");
        }
    }
}
