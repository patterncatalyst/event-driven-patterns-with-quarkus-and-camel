package com.patterncatalyst.shipping.batch.runner;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/** QUEUE=off and PRIORITY=off leave the Kueue labels off, so the Job starts at once and nothing admits or ranks it. */
@QuarkusTest
@TestProfile(QueueOffProfile.class)
class DemoSwitchesTest extends CamelQuarkusTestSupport {

    private static final FakeCluster CLUSTER = new FakeCluster();

    @Inject
    ProducerTemplate producer;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    void theLabelsAreLeftOff() throws Exception {
        ClusterRoutes.advise(context, CLUSTER);
        String id = given().contentType(ContentType.JSON)
                .body("{\"from\":\"2026-03-01T00:00:00Z\",\"to\":\"2026-03-02T00:00:00Z\",\"priority\":\"batch-low\"}")
                .post("/runs").then().statusCode(202).extract().path("id");
        producer.sendBody("direct:tick", null);
        var labels = CLUSTER.job("rec-" + id + "-plan-a1").getMetadata().getLabels();
        assertFalse(labels.containsKey("kueue.x-k8s.io/queue-name"));
        assertFalse(labels.containsKey("kueue.x-k8s.io/priority-class"));
        assertEquals(id, labels.get("edp.batch/run"));
        assertFalse(CLUSTER.job("rec-" + id + "-plan-a1").getSpec().getSuspend());
    }
}
