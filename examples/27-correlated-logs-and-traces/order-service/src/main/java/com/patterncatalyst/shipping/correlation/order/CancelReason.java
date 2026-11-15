package com.patterncatalyst.shipping.correlation.order;

/** Why a saga ended in CANCELLED. The reason travels on OrderCancelled. */
public enum CancelReason {
    OUT_OF_STOCK, PAYMENT_DECLINED, TIMED_OUT
}
