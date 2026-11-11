package com.patterncatalyst.shipping.queues;

/** What one delivery attempt came to, as it is written to the log. */
public enum Outcome {
    SENT, FAILED, REJECTED;

    public String text() {
        return name().toLowerCase();
    }
}
