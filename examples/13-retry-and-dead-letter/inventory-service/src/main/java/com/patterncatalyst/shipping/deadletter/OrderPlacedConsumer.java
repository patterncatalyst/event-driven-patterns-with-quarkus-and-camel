package com.patterncatalyst.shipping.deadletter;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reads the raw bytes of each record, so a failure leaves the original record intact for the dead letter queue.
 * A record that can never succeed (unparseable or invalid) aborts the retry at once; anything else is retried.
 * The retry count comes from configuration (retry.max) and replaces the value in the annotation.
 */
@ApplicationScoped
public class OrderPlacedConsumer {

    @Inject
    ObjectMapper json;

    @Inject
    ReservationService reservations;

    @Incoming("orders-placed")
    @Blocking
    @Retry(maxRetries = 8, delay = 1000, retryOn = Exception.class,
            abortOn = { IllegalArgumentException.class, JsonProcessingException.class })
    public void on(byte[] raw) throws Exception {
        reservations.reserve(json.readValue(raw, OrderPlaced.class));
    }
}
