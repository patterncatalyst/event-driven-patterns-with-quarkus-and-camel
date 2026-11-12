package com.patterncatalyst.shipping.aggregate;

/** Another writer appended to the stream after the version this command was decided against. */
public class ConcurrencyConflict extends RuntimeException {

    public ConcurrencyConflict(String streamId, int expectedVersion, Throwable cause) {
        super("stream " + streamId + " moved past version " + expectedVersion, cause);
    }
}
