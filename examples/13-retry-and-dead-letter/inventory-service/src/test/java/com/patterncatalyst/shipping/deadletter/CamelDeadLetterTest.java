package com.patterncatalyst.shipping.deadletter;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
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
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The Camel route: the dead letter channel keeps the original body, a non-retryable failure is tried once. */
@QuarkusTest
@TestProfile(CamelProfile.class)
class CamelDeadLetterTest {

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
    void validRecordIsReserved() throws Exception {
        String id = id();
        int before = Records.stock(em, "SKU-14");
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, Records.valid(id, "SKU-14", 1));
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> assertEquals(before - 1, Records.stock(em, "SKU-14")));
    }

    @Test
    void malformedRecordGoesToTheDeadLetterTopicWithItsBytes() throws Exception {
        String id = id();
        String bytes = "{\"orderId\":\"" + id + "\",";
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, bytes);

        var dead = Records.deadLetters(bootstrap, id, Duration.ofSeconds(20));
        assertEquals(1, dead.size());
        assertArrayEquals(bytes.getBytes(StandardCharsets.UTF_8), dead.get(0).value());
        verify(reservations, never()).reserve(argThat(o -> o != null && id.equals(o.orderId())));
    }

    @Test
    void invalidRecordIsDeadLetteredAfterOneAttempt() throws Exception {
        String id = id();
        String bytes = Records.valid(id, "SKU-15", 0);
        Records.send(bootstrap, Topics.ORDERS_PLACED, id, bytes);

        var dead = Records.deadLetters(bootstrap, id, Duration.ofSeconds(20));
        assertEquals(1, dead.size());
        assertArrayEquals(bytes.getBytes(StandardCharsets.UTF_8), dead.get(0).value());
        verify(reservations, times(1)).reserve(argThat(o -> id.equals(o.orderId())));
    }
}
