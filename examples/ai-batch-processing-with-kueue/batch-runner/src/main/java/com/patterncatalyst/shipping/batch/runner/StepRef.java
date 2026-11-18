package com.patterncatalyst.shipping.batch.runner;

/** What the reconcile tick needs to know about a step: enough to name and find its Job. */
public record StepRef(String runId, String name, int attempt, String status, String priority, String traceparent) {

    public static final String PLAN = "plan";
    public static final String RECONCILE = "reconcile";
    public static final String AGGREGATE = "aggregate";

    /** The Job of this attempt: rec-&lt;run&gt;-&lt;step&gt;-a&lt;attempt&gt;. */
    public String jobName() {
        return "rec-" + runId + "-" + name + "-a" + attempt;
    }

    public boolean running() {
        return Step.RUNNING.equals(status);
    }
}
