package com.patterncatalyst.shipping.evolution;

/** The aggregate's answer when a command breaks one of its rules. */
public class CommandRefused extends RuntimeException {

    public CommandRefused(String reason) {
        super(reason);
    }
}
