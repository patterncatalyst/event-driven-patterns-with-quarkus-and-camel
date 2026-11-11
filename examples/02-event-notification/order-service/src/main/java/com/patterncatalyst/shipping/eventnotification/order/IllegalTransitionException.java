package com.patterncatalyst.shipping.eventnotification.order;

import com.patterncatalyst.shipping.OrderStatus;

public class IllegalTransitionException extends RuntimeException {
    public IllegalTransitionException(String id, OrderStatus from) {
        super("order " + id + " cannot be cancelled from " + from);
    }
}
