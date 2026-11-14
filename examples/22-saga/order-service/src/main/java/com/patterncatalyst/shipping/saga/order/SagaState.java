package com.patterncatalyst.shipping.saga.order;

/** Where an order is in its saga. COMPLETED and CANCELLED are final. */
public enum SagaState {
    AWAITING_STOCK, AWAITING_PAYMENT, COMPLETED, CANCELLED
}
