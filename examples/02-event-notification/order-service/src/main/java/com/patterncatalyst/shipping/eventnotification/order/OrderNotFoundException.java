package com.patterncatalyst.shipping.eventnotification.order;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String id) {
        super("order " + id + " not found");
    }
}
