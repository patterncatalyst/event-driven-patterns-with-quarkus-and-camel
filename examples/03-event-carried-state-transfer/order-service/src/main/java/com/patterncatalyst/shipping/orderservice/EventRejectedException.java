package com.patterncatalyst.shipping.orderservice;

/** The producer refused the event after the order committed. */
class EventRejectedException extends RuntimeException {

    EventRejectedException(String message) {
        super(message);
    }
}
