package com.patterncatalyst.shipping.orchestration.order;

import static io.restassured.RestAssured.given;

import java.util.UUID;

import io.quarkus.narayana.jta.QuarkusTransaction;

/** Test helpers: start an order under one orchestrator, read its status. */
final class Orders {

    private Orders() {
    }

    static String id() {
        return "O-" + UUID.randomUUID();
    }

    static void start(String orchestrator, String id) {
        given().contentType("application/json")
                .body("{\"id\":\"" + id + "\",\"customerId\":\"CU-23\",\"itemSku\":\"SKU-23\",\"quantity\":1,\"amount\":100.00}")
                .post("/orders/" + orchestrator).then().statusCode(202);
    }

    /** A PLACED order that no orchestrator has been asked to run. */
    static void startRow(String id) {
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            OrderEntity row = new OrderEntity();
            row.id = id;
            row.customerId = "CU-23";
            row.itemSku = "SKU-23";
            row.quantity = 1;
            row.amount = new java.math.BigDecimal("100.00");
            row.status = "PLACED";
            row.orchestrator = "camel-lra";
            row.persist();
        });
    }

    static String status(String id) {
        return given().get("/orders/" + id).then().statusCode(200).extract().path("status");
    }

    /** The OrderCancelled rows in the outbox: the tests keep them, the compose run lets the connector read them. */
    static long cancelledRows(String orderId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) io.quarkus.hibernate.orm.panache.Panache.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM orders.outboxevent WHERE aggregateid = :id AND type = 'OrderCancelled'")
                .setParameter("id", orderId).getSingleResult()).longValue());
    }

    static long instanceRows(String instanceId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) io.quarkus.hibernate.orm.panache.Panache.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM orders.workflow_instance_entity WHERE instance_id = :id")
                .setParameter("id", instanceId).getSingleResult()).longValue());
    }
}
