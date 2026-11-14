package com.patterncatalyst.shipping.saga.order;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.patterncatalyst.shipping.Topics;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** The service end to end: the HTTP edge, and the replies as Kafka records with an id header. */
@QuarkusTest
class SagaMessagingTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    EntityManager em;

    private static int post(String id) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("id", id, "customerId", "CU-22", "itemSku", "SKU-22", "quantity", 1, "amount", 25.00))
                .post("/orders").statusCode();
    }

    @Test
    void anOrderPostedOverHttpHasASagaAndAnOutboxRowAndAnOrderIdIsUnique() {
        String id = Fixtures.newId();
        assertEquals(201, post(id));
        assertEquals(409, post(id));
        assertEquals(SagaState.AWAITING_STOCK, Fixtures.state(id));
        assertEquals(1, Fixtures.outbox(em, "OrderPlaced", id));
        given().get("/orders/" + id).then().statusCode(200).body("saga", org.hamcrest.Matchers.equalTo("AWAITING_STOCK"));
    }

    @Test
    void repliesOnTheTopicsMoveTheSaga() throws Exception {
        String id = Fixtures.newId();
        assertEquals(201, post(id));
        Fixtures.send(bootstrapServers, Topics.INVENTORY_RESERVED, id, UUID.randomUUID(),
                "{\"orderId\":\"%s\",\"sku\":\"SKU-22\",\"quantity\":1,\"amount\":25.00}".formatted(id));
        Fixtures.await(() -> Fixtures.state(id) == SagaState.AWAITING_PAYMENT);
        Fixtures.send(bootstrapServers, Topics.PAYMENTS_PROCESSED, id, UUID.randomUUID(),
                "{\"payment\":{\"id\":\"P-%s\",\"orderId\":\"%s\",\"amount\":25.00,\"status\":\"COMPLETED\"}}".formatted(id, id));
        Fixtures.await(() -> Fixtures.state(id) == SagaState.COMPLETED);
        assertEquals("PAID", Fixtures.orderStatus(id));
    }

    @Test
    void aFailedPaymentOnTheTopicCancelsTheOrder() throws Exception {
        String id = Fixtures.newId();
        assertEquals(201, post(id));
        Fixtures.send(bootstrapServers, Topics.INVENTORY_RESERVED, id, UUID.randomUUID(),
                "{\"orderId\":\"%s\",\"sku\":\"SKU-22\",\"quantity\":1,\"amount\":900.00}".formatted(id));
        Fixtures.await(() -> Fixtures.state(id) == SagaState.AWAITING_PAYMENT);
        Fixtures.send(bootstrapServers, Topics.PAYMENTS_FAILED, id, UUID.randomUUID(),
                "{\"orderId\":\"%s\",\"reason\":\"DECLINED\"}".formatted(id));
        Fixtures.await(() -> Fixtures.state(id) == SagaState.CANCELLED);
        assertEquals(1, Fixtures.outbox(em, "OrderCancelled", id));
    }
}
