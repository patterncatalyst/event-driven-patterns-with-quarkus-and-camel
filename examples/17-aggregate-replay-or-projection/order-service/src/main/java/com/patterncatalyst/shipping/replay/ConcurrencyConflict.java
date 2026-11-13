package com.patterncatalyst.shipping.replay;

/** Another writer appended to the stream first. */
public class ConcurrencyConflict extends RuntimeException {

    public ConcurrencyConflict(String streamId, int expectedVersion, Throwable cause) {
        super("stream " + streamId + " moved past version " + expectedVersion, cause);
    }
}
