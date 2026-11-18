package com.patterncatalyst.shipping.batch.worker;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** The offset ranges the plan recorded for one partition of both topics, and how far the partition got. */
@Entity
@Table(name = "run_partition")
@IdClass(RunPartition.Key.class)
public class RunPartition extends PanacheEntityBase {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";

    @Id
    @Column(name = "run_id")
    public String runId;

    @Id
    public int partition;

    @Column(name = "scheduled_from")
    public long scheduledFrom;

    @Column(name = "scheduled_to")
    public long scheduledTo;

    @Column(name = "delivered_from")
    public long deliveredFrom;

    @Column(name = "delivered_to")
    public long deliveredTo;

    public String status;

    public int attempts;

    @Column(name = "records_read")
    public long recordsRead;

    @Column(name = "completed_at")
    public Instant completedAt;

    public static class Key implements Serializable {
        public String runId;
        public int partition;

        public Key() {
        }

        public Key(String runId, int partition) {
            this.runId = runId;
            this.partition = partition;
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
