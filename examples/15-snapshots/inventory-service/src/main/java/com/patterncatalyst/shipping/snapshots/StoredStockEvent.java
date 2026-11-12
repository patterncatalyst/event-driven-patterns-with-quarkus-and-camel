package com.patterncatalyst.shipping.snapshots;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of inventory.events. */
@Entity
@Immutable
@Table(name = "events")
public class StoredStockEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long position;

    @Column(name = "event_id")
    public UUID eventId;

    @Column(name = "stream_type")
    public String streamType;

    @Column(name = "stream_id")
    public String streamId;

    public int version;

    public String type;

    @JdbcTypeCode(SqlTypes.JSON)
    public StockEvent payload;

    @Column(name = "occurred_at")
    public Instant occurredAt;
}
