package com.patterncatalyst.shipping.batch.worker;

import java.io.Serializable;
import java.util.Objects;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** The run's totals per carrier, written by aggregate. */
@Entity
@Table(name = "carrier_total")
@IdClass(CarrierTotal.Key.class)
public class CarrierTotal extends PanacheEntityBase {

    @Id
    @Column(name = "run_id")
    public String runId;

    @Id
    public String carrier;

    public long scheduled;

    public long delivered;

    public long open;

    public static class Key implements Serializable {
        public String runId;
        public String carrier;

        public Key() {
        }

        public Key(String runId, String carrier) {
            this.runId = runId;
            this.carrier = carrier;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(runId, k.runId) && Objects.equals(carrier, k.carrier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, carrier);
        }
    }
}
