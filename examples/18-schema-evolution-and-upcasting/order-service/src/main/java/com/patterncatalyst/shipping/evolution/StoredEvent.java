package com.patterncatalyst.shipping.evolution;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the append-only events table. The payload stays raw JSON: its shape depends on schema_version. */
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

    @Column(name = "schema_version")
    public short schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    public JsonNode payload;

    @Column(name = "occurred_at")
    public Instant occurredAt;
}
