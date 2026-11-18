package com.patterncatalyst.shipping.batch.worker;

import java.io.Serializable;
import java.util.Objects;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** What one partition counted for one carrier. Written in the transaction that marks the partition DONE. */
@Entity
@Table(name = "partition_result")
@IdClass(PartitionResult.Key.class)
public class PartitionResult extends PanacheEntityBase {

    @Id
    @Column(name = "run_id")
    public String runId;

    @Id
    public int partition;

    @Id
    public String carrier;

    public long scheduled;

    public long delivered;

    public long open;

    public static class Key implements Serializable {
        public String runId;
        public int partition;
        public String carrier;

        public Key() {
        }

        public Key(String runId, int partition, String carrier) {
            this.runId = runId;
            this.partition = partition;
            this.carrier = carrier;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && partition == k.partition && Objects.equals(runId, k.runId)
                    && Objects.equals(carrier, k.carrier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, partition, carrier);
        }
    }
}
