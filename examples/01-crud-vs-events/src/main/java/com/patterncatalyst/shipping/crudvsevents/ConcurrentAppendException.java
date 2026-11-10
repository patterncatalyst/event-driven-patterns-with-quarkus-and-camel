package com.patterncatalyst.shipping.crudvsevents;

class ConcurrentAppendException extends RuntimeException {
    ConcurrentAppendException(String message, Throwable cause) {
        super(message, cause);
    }
}
