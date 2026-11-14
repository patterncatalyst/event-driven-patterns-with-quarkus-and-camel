package com.patterncatalyst.shipping.decisions;

import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Stage two: a recorded analysis becomes a recorded decision. One rule unit instance per analysis, so the result is a
 * function of the record and the attempt limit, with no state kept between calls.
 */
@ApplicationScoped
public class DecisionService {

    public static final String RULES_VERSION = "v1";

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "decisions.attempt-limit", defaultValue = "3")
    int attemptLimit;

    public DeliveryExceptionDecided decide(DeliveryExceptionAnalysed analysed) {
        return decide(analysed, attemptLimit);
    }

    /** The same rules with another attempt limit; the replay endpoint uses it to show a rule change. */
    public DeliveryExceptionDecided decide(DeliveryExceptionAnalysed analysed, int limit) {
        Verdict verdict = fire(new Analysis(analysed.exceptionId(), analysed.reason().name(), analysed.attempt()), limit);
        return new DeliveryExceptionDecided(analysed.exceptionId(), analysed.orderId(), Resolution.valueOf(verdict.decision()),
                verdict.rule(), RULES_VERSION, analysed.occurredAt());
    }

    /** Decides and counts; the live route calls this, the replay does not. */
    public DeliveryExceptionDecided decideAndCount(DeliveryExceptionAnalysed analysed) {
        DeliveryExceptionDecided decided = decide(analysed);
        meters.counter("exception.decisions", "decision", decided.decision().name(), "rule", decided.rule()).increment();
        return decided;
    }

    Verdict fire(Analysis analysis, int limit) {
        var unit = new DecisionUnit();
        unit.setAttemptLimit(limit);
        unit.getAnalyses().add(analysis);
        try (RuleUnitInstance<DecisionUnit> instance = RuleUnitProvider.get().createRuleUnitInstance(unit)) {
            instance.fire();
        }
        if (unit.getVerdicts().size() != 1) {
            throw new IllegalStateException("expected exactly one rule to decide " + analysis + ", got " + unit.getVerdicts());
        }
        return unit.getVerdicts().get(0);
    }
}
