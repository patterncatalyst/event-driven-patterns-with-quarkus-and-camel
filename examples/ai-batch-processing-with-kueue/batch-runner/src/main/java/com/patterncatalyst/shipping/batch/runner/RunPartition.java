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

/** Read-only here: the runner sums the records the workers read. */
@Entity
@Table(name = "run_partition")
@IdClass(RunPartition.Key.class)
public class RunPartition extends PanacheEntityBase {

    @Id
    @Column(name = "run_id")
    public String runId;

    @Id
    public int partition;

    public String status;

    @Column(name = "records_read")
    public long recordsRead;

    @Column(name = "completed_at")
    public Instant completedAt;

    public static class Key implements Serializable {
        public String runId;
        public int partition;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && partition == k.partition && Objects.equals(runId, k.runId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, partition);
        }
    }
}
