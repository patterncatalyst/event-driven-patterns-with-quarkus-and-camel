package com.patterncatalyst.shipping.crudvsevents;

class OrderNotFoundException extends RuntimeException {
    OrderNotFoundException(String id) {
        super("no order " + id);
    }
}
