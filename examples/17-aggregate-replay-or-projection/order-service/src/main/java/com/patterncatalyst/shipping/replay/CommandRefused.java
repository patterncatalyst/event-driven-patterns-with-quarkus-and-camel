package com.patterncatalyst.shipping.replay;

/** The aggregate refuses a command; nothing is appended. */
public class CommandRefused extends RuntimeException {

    public CommandRefused(String reason) {
        super(reason);
    }
}
