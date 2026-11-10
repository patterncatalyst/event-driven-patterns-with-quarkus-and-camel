package com.patterncatalyst.shipping.crudvsevents;

class IllegalTransitionException extends RuntimeException {
    IllegalTransitionException(String message) {
        super(message);
    }
}
