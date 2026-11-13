package com.patterncatalyst.shipping.replay;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of {@code orders.events}. The {@code tx_id} column is written by its default and is not mapped. */
@Entity
@Immutable
@Table(name = "events")
public class StoredEvent {

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
    public OrderEvent payload;

    @Column(name = "occurred_at")
    public Instant occurredAt;
}
