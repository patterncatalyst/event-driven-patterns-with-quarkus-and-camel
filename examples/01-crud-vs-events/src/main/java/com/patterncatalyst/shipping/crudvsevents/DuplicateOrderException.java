package com.patterncatalyst.shipping.crudvsevents;

class DuplicateOrderException extends RuntimeException {
    DuplicateOrderException(String id) {
        super("order " + id + " already exists");
    }
}
