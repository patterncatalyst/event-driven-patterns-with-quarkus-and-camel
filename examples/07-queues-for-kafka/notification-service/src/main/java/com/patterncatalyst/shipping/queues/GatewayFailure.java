package com.patterncatalyst.shipping.queues;

/** The email provider refused an attempt. Transient failures are worth another attempt; permanent ones are not. */
public class GatewayFailure extends RuntimeException {

    private final boolean transientFailure;

    private GatewayFailure(String message, boolean transientFailure) {
        super(message);
        this.transientFailure = transientFailure;
    }

    public static GatewayFailure transientFailure(String message) {
        return new GatewayFailure(message, true);
    }

    public static GatewayFailure permanentFailure(String message) {
        return new GatewayFailure(message, false);
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
