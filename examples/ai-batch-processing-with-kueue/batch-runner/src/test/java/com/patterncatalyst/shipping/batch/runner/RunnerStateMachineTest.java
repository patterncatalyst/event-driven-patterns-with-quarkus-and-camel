package com.patterncatalyst.shipping.batch.runner;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(AdviceProfile.class)
class RunnerStateMachineTest extends CamelQuarkusTestSupport {

    private static final FakeCluster CLUSTER = new FakeCluster();
    private static final AtomicInteger WINDOWS = new AtomicInteger();

    @Inject
    ProducerTemplate producer;

    @Inject
    MeterRegistry exported;

    /** The exporting registry reports NaN when read back, so a registry of our own listens beside it. */
    private static final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @BeforeEach
    void routes() throws Exception {
        if (exported instanceof CompositeMeterRegistry composite && !composite.getRegistries().contains(registry)) {
            composite.add(registry);
        }
        ClusterRoutes.advise(context, CLUSTER);
        CLUSTER.reset();
        QuarkusTransaction.requiringNew().run(() -> {
            StepJob.deleteAll();
            RunPartition.deleteAll();
            Step.deleteAll();
            Run.deleteAll();
        });
    }

    private static String window() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z").plus(WINDOWS.incrementAndGet(), ChronoUnit.DAYS);
        return "\"from\":\"" + from + "\",\"to\":\"" + from.plus(1, ChronoUnit.DAYS) + "\"";
    }

    private static Response post(String body) {
        return given().contentType(ContentType.JSON).body(body).post("/runs");
    }

    private String accept(String extra) {
        return post("{" + window() + extra + "}").then().log().ifValidationFails().statusCode(202).extract().path("id");
    }

    private void tick() {
        producer.sendBody("direct:tick", null);
    }

    private static String stepStatus(String runId, String step) {
        return given().get("/runs/" + runId).then().statusCode(200).extract()
                .path("steps.find { it.name == '" + step + "' }.status");
    }

    private static String job(String runId, String step, int attempt) {
        return "rec-" + runId + "-" + step + "-a" + attempt;
    }

    /** A step goes PENDING, QUEUED when its Job exists, RUNNING when Kueue unsuspends it, DONE when it completes. */
    private void runStep(String runId, String step) {
        tick();
        assertEquals("QUEUED", stepStatus(runId, step));
        assertTrue(CLUSTER.job(job(runId, step, 1)).getSpec().getSuspend(), "created suspended");
        CLUSTER.suspend(job(runId, step, 1), false);
        tick();
        assertEquals("RUNNING", stepStatus(runId, step));
        CLUSTER.finish(job(runId, step, 1), "Complete");
        tick();
        assertEquals("DONE", stepStatus(runId, step));
    }

    @Test
    void stepsRunInOrderAndTheRunIsDone() {
        String id = accept(",\"priority\":\"batch-high\"");
        runStep(id, "plan");
        assertEquals(1, CLUSTER.names().size(), "the next Job waits for the plan to be DONE");
        runStep(id, "reconcile");

        Job reconcile = CLUSTER.job(job(id, "reconcile", 1));
        assertEquals("Indexed", reconcile.getSpec().getCompletionMode());
        assertEquals(3, reconcile.getSpec().getCompletions());
        assertEquals(3, reconcile.getSpec().getParallelism());
        assertEquals(1, reconcile.getSpec().getBackoffLimitPerIndex());
        assertEquals(3, reconcile.getSpec().getMaxFailedIndexes());
        assertEquals(2, reconcile.getSpec().getPodFailurePolicy().getRules().size());
        assertEquals("FailIndex", reconcile.getSpec().getPodFailurePolicy().getRules().get(0).getAction());
        assertEquals("reconciliation", reconcile.getMetadata().getLabels().get("kueue.x-k8s.io/queue-name"));
        assertEquals("batch-high", reconcile.getMetadata().getLabels().get("kueue.x-k8s.io/priority-class"));
        assertEquals(id, reconcile.getMetadata().getLabels().get("edp.batch/run"));
        assertEquals("reconcile", reconcile.getMetadata().getLabels().get("edp.batch/step"));
        var container = reconcile.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertEquals("500m", container.getResources().getRequests().get("cpu").toString());
        assertEquals("384Mi", container.getResources().getRequests().get("memory").toString());
        assertEquals("512Mi", container.getResources().getLimits().get("memory").toString());
        assertTrue(container.getEnv().stream().anyMatch(e -> e.getName().equals("JOB_INDEX_FAILURE_COUNT")
                && e.getValueFrom().getFieldRef().getFieldPath()
                        .equals("metadata.annotations['batch.kubernetes.io/job-index-failure-count']")));
        assertTrue(container.getEnv().stream().anyMatch(e -> e.getName().equals("RUN_ID") && e.getValue().equals(id)));
        assertEquals("Never", reconcile.getSpec().getTemplate().getSpec().getRestartPolicy());

        runStep(id, "aggregate");
        Job aggregate = CLUSTER.job(job(id, "aggregate", 1));
        assertEquals("250m", aggregate.getSpec().getTemplate().getSpec().getContainers().get(0).getResources()
                .getRequests().get("cpu").toString());
        given().get("/runs/" + id).then().statusCode(200).body("status", equalTo("DONE"))
                .body("steps", hasSize(3));
        tick();
        assertEquals(3, CLUSTER.names().size(), "a finished run creates nothing more");
    }

    @Test
    void aWindowIsAcceptedOnce() {
        String body = "{" + window() + "}";
        post(body).then().statusCode(202);
        post(body).then().statusCode(409);
    }

    @Test
    void badRequestsAreRefused() {
        post("{" + window() + ",\"priority\":\"urgent\"}").then().statusCode(400);
        post("{\"from\":\"2026-02-02T00:00:00Z\",\"to\":\"2026-02-01T00:00:00Z\"}").then().statusCode(400);
        post("not json").then().statusCode(400);
        given().get("/runs/nope").then().statusCode(404);
    }

    @Test
    void aFailedStepIsReplayedAloneAndOnlyAFailedRunReplays() {
        String id = accept("");
        runStep(id, "plan");
        given().post("/runs/" + id + "/replay").then().statusCode(409);

        tick();
        CLUSTER.suspend(job(id, "reconcile", 1), false);
        tick();
        CLUSTER.finish(job(id, "reconcile", 1), "Failed");
        tick();
        assertEquals("FAILED", stepStatus(id, "reconcile"));
        given().get("/runs/" + id).then().body("status", equalTo("FAILED"));
        tick();
        assertEquals(2, CLUSTER.names().size(), "no aggregate Job after a failed reconcile");
        double failures = registry.find("batch.step.failures").tag("step", "reconcile").counter().count();
        assertEquals(1.0, failures);

        given().post("/runs/" + id + "/replay").then().statusCode(202);
        assertEquals("PENDING", stepStatus(id, "reconcile"));
        assertEquals("DONE", stepStatus(id, "plan"));
        tick();
        assertNotNull(CLUSTER.job(job(id, "reconcile", 2)), "attempt 2 of the failed step");
        assertEquals(1, CLUSTER.names().stream().filter(n -> n.contains("-plan-")).count(), "plan is not repeated");
        given().get("/runs/" + id).then().body("steps.find { it.name == 'reconcile' }.attempt", equalTo(2));
        assertEquals(1.0, registry.find("batch.step.replays").counter().count());
        // the same window is not open for a second run while the replay is under way
        post("{\"from\":\"" + Run.<Run> findAll().firstResult().windowFrom + "\",\"to\":\""
                + Run.<Run> findAll().firstResult().windowTo + "\"}").then().statusCode(409);
    }

    @Test
    void aSuspendedAgainJobGoesBackToTheQueueAndDoesNotFail() {
        String id = accept("");
        runStep(id, "plan");
        tick();
        String name = job(id, "reconcile", 1);
        CLUSTER.suspend(name, false);
        tick();
        assertEquals("RUNNING", stepStatus(id, "reconcile"));
        double before = count("batch.step.preemptions");

        CLUSTER.suspend(name, true);
        tick();
        assertEquals("QUEUED", stepStatus(id, "reconcile"));
        given().get("/runs/" + id).then().body("status", equalTo("RUNNING"));
        assertEquals(before + 1, count("batch.step.preemptions"));
        assertEquals(0.0, count("batch.step.failures"));

        CLUSTER.suspend(name, false);
        tick();
        assertEquals("RUNNING", stepStatus(id, "reconcile"));
        assertEquals(before + 1, count("batch.step.preemptions"));
    }

    @Test
    void anAdmittedJobThatVanishesFailsTheStep() {
        String id = accept("");
        tick();
        CLUSTER.suspend(job(id, "plan", 1), false);
        tick();
        CLUSTER.jobs.clear();
        tick();
        assertEquals("FAILED", stepStatus(id, "plan"));
        assertNull(CLUSTER.job(job(id, "plan", 1)));
        assertFalse(CLUSTER.names().contains(job(id, "plan", 2)));
    }

    private double count(String name) {
        var counter = registry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
