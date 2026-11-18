package com.patterncatalyst.shipping.batch.runner;

import java.io.Serializable;
import java.util.Objects;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** The Job of one attempt of a step. */
@Entity
@Table(name = "step_job")
@IdClass(StepJob.Key.class)
public class StepJob extends PanacheEntityBase {

    @Id
    @Column(name = "run_id")
    public String runId;

    @Id
    public String name;

    @Id
    public int attempt;

    @Column(name = "job_name")
    public String jobName;

    public static class Key implements Serializable {
        public String runId;
        public String name;
        public int attempt;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && attempt == k.attempt && Objects.equals(runId, k.runId)
                    && Objects.equals(name, k.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, name, attempt);
        }
    }
}
