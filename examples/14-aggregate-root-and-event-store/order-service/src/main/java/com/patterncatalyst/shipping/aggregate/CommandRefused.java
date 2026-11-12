package com.patterncatalyst.shipping.aggregate;

/** The aggregate's answer when a command breaks one of its rules. A business outcome, not a failure. */
public class CommandRefused extends RuntimeException {

    public CommandRefused(String reason) {
        super(reason);
    }
}
