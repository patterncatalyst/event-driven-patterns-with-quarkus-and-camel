package com.patterncatalyst.shipping.batch.runner;

/** A request the runner refuses; the status is the HTTP status of the answer. */
public class BatchException extends RuntimeException {

    private final int status;

    public BatchException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
