package com.patterncatalyst.shipping.analysis;

/** The closed set of reasons a carrier's note can be reduced to, reused unchanged from chapter 24. UNKNOWN is the
 * exit for anything else. */
public enum Reason {
    DAMAGED, LOST, RECIPIENT_ABSENT, ADDRESS_PROBLEM, REFUSED, DELAYED, UNKNOWN
}
