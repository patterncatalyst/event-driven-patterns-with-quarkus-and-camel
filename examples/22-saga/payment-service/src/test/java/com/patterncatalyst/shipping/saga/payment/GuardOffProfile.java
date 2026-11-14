package com.patterncatalyst.shipping.saga.payment;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Demo code switched on: PAYMENT_CANCEL_GUARD=off. */
public class GuardOffProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("payment.cancel-guard", "off");
    }
}
