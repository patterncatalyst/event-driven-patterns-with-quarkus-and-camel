package com.patterncatalyst.shipping.decisions;

import java.util.ArrayList;
import java.util.List;

import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

/** The rule unit: the analyses to decide, the verdicts the rules write, and the one parameter a rule reads. */
public class DecisionUnit implements RuleUnitData {

    private final DataStore<Analysis> analyses = DataSource.createStore();
    private final List<Verdict> verdicts = new ArrayList<>();
    private int attemptLimit = 3;

    public DataStore<Analysis> getAnalyses() {
        return analyses;
    }

    public List<Verdict> getVerdicts() {
        return verdicts;
    }

    public int getAttemptLimit() {
        return attemptLimit;
    }

    public void setAttemptLimit(int attemptLimit) {
        this.attemptLimit = attemptLimit;
    }
}
