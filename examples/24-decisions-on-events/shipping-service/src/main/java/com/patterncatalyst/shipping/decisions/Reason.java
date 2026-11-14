package com.patterncatalyst.shipping.decisions;

/** The closed set of reasons a carrier's note can be reduced to. UNKNOWN is the exit for anything else. */
public enum Reason {
    DAMAGED, LOST, RECIPIENT_ABSENT, ADDRESS_PROBLEM, REFUSED, DELAYED, UNKNOWN
}
