package com.patterncatalyst.shipping.orchestration.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletionException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

/** Only the coordinator's refusal of a join is handled as a refusal; any other failure is not. */
class RefusedJoinTest {

    private static Exchange failedWith(Exception e) {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, e);
        return exchange;
    }

    @Test
    void aRefusedJoinIsRecognisedThroughItsWrappers() {
        var refusal = new RuntimeCamelException("Cannot join LRA http://coordinator/lra-coordinator/1 (HTTP 404): Cannot find transaction id");
        assertTrue(TakePaymentRoute.isRefusedJoin(failedWith(new CompletionException(refusal))));
    }

    @Test
    void aDatabaseOrBrokerFailureIsNotARefusedJoin() {
        assertFalse(TakePaymentRoute.isRefusedJoin(failedWith(new RuntimeCamelException("could not execute statement"))));
        assertFalse(TakePaymentRoute.isRefusedJoin(failedWith(new CompletionException(new java.io.IOException("Connection refused")))));
    }
}
