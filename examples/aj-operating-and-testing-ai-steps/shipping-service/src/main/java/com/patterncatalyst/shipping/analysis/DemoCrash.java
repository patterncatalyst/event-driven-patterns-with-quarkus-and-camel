package com.patterncatalyst.shipping.analysis;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Demo code, not part of the pattern. For the exception id named in demo.crash-after-analysis (env
 * DEMO_CRASH_AFTER_ANALYSIS) this throws once, after {@link MemoizedAnalysisService} has committed the row and
 * before the route publishes the record, forcing one redelivery. The redelivery calls {@link
 * MemoizedAnalysisService#analyseOnce} again, which finds the committed row and reuses it instead of calling the
 * model a second time: the guard is the database row, not anything held in this bean, so an actual process kill
 * (as chapter 11's crash switch demonstrates) would prove the same thing, just not inside one test JVM.
 */
@ApplicationScoped
public class DemoCrash {

    @ConfigProperty(name = "demo.crash-after-analysis")
    Optional<String> exceptionId;

    private final Set<String> crashed = ConcurrentHashMap.newKeySet();

    public void afterAnalysis(DeliveryExceptionAnalysed analysed) {
        String id = analysed.exceptionId();
        if (exceptionId.filter(id::equals).isPresent() && crashed.add(id)) {
            throw new IllegalStateException("demo crash after analysis for " + id);
        }
    }
}
