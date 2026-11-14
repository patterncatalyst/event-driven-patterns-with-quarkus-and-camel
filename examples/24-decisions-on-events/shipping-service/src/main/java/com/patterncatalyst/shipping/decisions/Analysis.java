package com.patterncatalyst.shipping.decisions;

/** The fact the rules see: the recorded reason and the carrier's attempt number, as plain values. */
public record Analysis(String exceptionId, String reason, int attempt) {
}
