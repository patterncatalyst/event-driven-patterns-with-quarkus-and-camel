package com.patterncatalyst.shipping.snapshots;

/** Another writer appended to the stream first: the unique (stream_id, version) constraint refused the append. */
public class ConcurrencyConflict extends RuntimeException {

    public ConcurrencyConflict(String message, Throwable cause) {
        super(message, cause);
    }
}
