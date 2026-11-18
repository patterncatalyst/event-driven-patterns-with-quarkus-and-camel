package com.patterncatalyst.shipping.batch.runner;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** One step of a run: plan, reconcile or aggregate. */
@Entity
@Table(name = "step")
@IdClass(Step.Key.class)
public class Step extends PanacheEntityBase {

    public static final String PENDING = "PENDING";
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Id
    @Column(name = "run_id")
    public String runId;

    @Id
    public String name;

    public int ordinal;

    public String status;

    public int attempt;

    @Column(name = "job_name")
    public String jobName;

    /** When the current Job was created or, after a preemption, put back in the queue. */
    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "admitted_at")
    public Instant admittedAt;

    @Column(name = "finished_at")
    public Instant finishedAt;

    public static class Key implements Serializable {
        public String runId;
        public String name;

        public Key() {
        }

        public Key(String runId, String name) {
            this.runId = runId;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(runId, k.runId) && Objects.equals(name, k.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, name);
        }
    }
}
