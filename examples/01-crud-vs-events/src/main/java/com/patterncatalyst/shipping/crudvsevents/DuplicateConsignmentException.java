package com.patterncatalyst.shipping.crudvsevents;

class DuplicateConsignmentException extends RuntimeException {
    DuplicateConsignmentException(String id) {
        super("consignment " + id + " already exists");
    }
}
