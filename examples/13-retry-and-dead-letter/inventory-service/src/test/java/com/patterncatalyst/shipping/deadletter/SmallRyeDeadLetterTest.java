package com.patterncatalyst.shipping.deadletter;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The SmallRye consumer: dead letters keep the original record, a transient failure is retried. */
@QuarkusTest
class SmallRyeDeadLetterTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    @Inject
    EntityManager em;

    @InjectSpy
    ReservationService reservations;

    private static String id() {
        return "O-" + UUID.randomUUID();
    }

    @Test
    void malformedRecordGoesToTheDeadLetterTopicWithItsBytesAndSourcePosition() throws Exception {
        String id = id();
        String bytes = "{\"orderId\":\"" + id + "\",";
        long offset = Records.send(bootstrap, Topics.ORDERS_PLACED, id, bytes);

        var dead = Records.deadLetters(bootstrap, id, Duration.ofSeconds(15));
        assertEquals(1, dead.size());
        assertArrayEquals(bytes.getBytes(StandardCharsets.UTF_8), dead.get(0).value());
        assertEquals(id, dead.get(0).key());
        assertEquals(Topics.ORDERS_PLACED, Records.header(dead.get(0), "dead-letter-topic"));
        assertEquals(Long.toString(offset), Records.header(dead.get(0), "dead-letter-offset"));
        verify(reservations, never()).reserve(argThat(o -> o != null && id.equals(o.orderId())));
    }

    @Test
    void invalidRecordIsDeadLetteredAfterOneAttempt() throws Exception {
        String id = id();
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, Records.valid(id, "SKU-12", 0));

        var dead = Records.deadLetters(bootstrap, id, Duration.ofSeconds(15));
        assertEquals(1, dead.size());
        assertTrue(Records.header(dead.get(0), "dead-letter-reason").contains("quantity must be positive"));
        verify(reservations, times(1)).reserve(argThat(o -> id.equals(o.orderId())));
    }

    @Test
    void lockTimeoutIsRetriedThenReserved() throws Exception {
        String id = id();
        int before = Records.stock(em, "SKU-11");
        Records.lockFor(em, "SKU-11", Duration.ofSeconds(5));
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, Records.valid(id, "SKU-11", 1));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> assertEquals(before - 1, Records.stock(em, "SKU-11")));
        verify(reservations, atLeast(2)).reserve(argThat(o -> id.equals(o.orderId())));
        assertTrue(Records.deadLetters(bootstrap, id, Duration.ofSeconds(3)).isEmpty());
    }
}
