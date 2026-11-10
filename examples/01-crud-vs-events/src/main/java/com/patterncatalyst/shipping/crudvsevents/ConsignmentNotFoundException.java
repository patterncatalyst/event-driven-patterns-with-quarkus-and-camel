package com.patterncatalyst.shipping.crudvsevents;

class ConsignmentNotFoundException extends RuntimeException {
    ConsignmentNotFoundException(String id) {
        super("no consignment " + id);
    }
}
