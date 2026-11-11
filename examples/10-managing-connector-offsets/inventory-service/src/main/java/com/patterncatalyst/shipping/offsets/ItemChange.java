package com.patterncatalyst.shipping.offsets;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One change event received from the connector: what happened to which SKU, and the WAL position it carries. */
@Entity
@Table(name = "item_change")
public class ItemChange extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String op;
    public String sku;
    public int qty;
    public long lsn;
    @Column(name = "received_at")
    public Instant receivedAt;
}
