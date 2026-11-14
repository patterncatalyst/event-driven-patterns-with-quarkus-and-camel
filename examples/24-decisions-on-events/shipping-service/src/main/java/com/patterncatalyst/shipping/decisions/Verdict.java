package com.patterncatalyst.shipping.decisions;

/** What a rule's right-hand side writes: the decision and the name of the rule that made it. */
public record Verdict(String exceptionId, String decision, String rule) {
}
