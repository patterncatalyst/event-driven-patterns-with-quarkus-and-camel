package com.patterncatalyst.shipping.snapshots;

import org.eclipse.microprofile.faulttolerance.Retry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * A lost race on a hot SKU is retried by loading and deciding again. The bound is generous because eight writers on
 * one stream conflict often; maxRetries can be overridden under this method's config key.
 */
@ApplicationScoped
public class ReservationCommands {

    @Inject
    ReservationTransaction transaction;

    @Retry(retryOn = ConcurrencyConflict.class, maxRetries = 30, delay = 10, jitter = 30)
    public ReservationTransaction.Result reserve(String sku, String orderId, int quantity) {
        return transaction.reserve(sku, orderId, quantity);
    }

    @Retry(retryOn = ConcurrencyConflict.class, maxRetries = 30, delay = 10, jitter = 30)
    public ReservationTransaction.Result receive(String sku, int quantity) {
        return transaction.receive(sku, quantity);
    }
}
