package com.patterncatalyst.shipping.ordering;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A shipment event that arrived before its turn, kept as the JSON it arrived as. */
@Entity
@Table(name = "pending_shipment_event")
public class PendingShipmentEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_id")
    public String orderId;
    public int sequence;
    public String payload;
}
