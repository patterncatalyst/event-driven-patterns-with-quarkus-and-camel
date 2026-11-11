package com.patterncatalyst.shipping.eventnotification.order;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String id) {
        super("order " + id + " already exists");
    }
}
