package com.patterncatalyst.shipping.resequencing;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An event the service applied. The identity id records the order of application. */
@Entity
@Table(name = "shipment_applied")
public class AppliedShipment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_id")
    public String orderId;
    @Column(name = "event_type")
    public String eventType;
    public int sequence;
    @Column(name = "global_sequence")
    public long globalSequence;
    @Column(name = "applied_at", insertable = false, updatable = false)
    public Instant appliedAt;
}
