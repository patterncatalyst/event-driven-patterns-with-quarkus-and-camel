package com.patterncatalyst.shipping.join;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * A join needs both topics to have the same number of partitions. With three and two, Kafka Streams starts and reports
 * healthy, and the join matches only the orders whose two records happen to land on the same partition number. The
 * Camel route reads both topics in one consumer and matches every order.
 */
@QuarkusTest
@TestProfile(MismatchedPartitionsProfile.class)
class CopartitioningTest {

    private static final int ORDERS = 30;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Test
    void streamsMatchesSomeOrdersAndCamelMatchesAll() throws Exception {
        for (int i = 0; i < ORDERS; i++) {
            Kafka.reserve(bootstrap, "O-P%02d".formatted(i), "09:00");
        }
        for (int i = 0; i < ORDERS; i++) {
            Kafka.pay(bootstrap, "O-P%02d".formatted(i), "09:01");
        }
        Kafka.await(() -> unchecked(() -> ready(Topics.SHIPPING_ORDER_READINESS_CAMEL) == ORDERS));
        Thread.sleep(5000);
        long camel = ready(Topics.SHIPPING_ORDER_READINESS_CAMEL);
        long streams = ready(Topics.SHIPPING_ORDER_READINESS);
        assertEquals(ORDERS, camel);
        assertTrue(streams > 0 && streams < ORDERS, "Streams matched " + streams + " of " + ORDERS);
    }

    private long ready(String topic) throws Exception {
        return Kafka.readiness(bootstrap, topic).stream()
                .filter(r -> r.orderId().startsWith("O-P") && r.ready()).count();
    }

    private static boolean unchecked(java.util.concurrent.Callable<Boolean> check) {
        try {
            return check.call();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
