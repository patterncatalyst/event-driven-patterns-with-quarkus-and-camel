package com.patterncatalyst.shipping.snapshots;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The latest snapshot of one stream. Read through Panache; written by SnapshotStore's guarded upsert. */
@Entity
@Table(name = "snapshots")
public class SnapshotRow extends PanacheEntityBase {

    @Id
    @Column(name = "stream_id")
    public String streamId;

    public int version;

    @Column(name = "schema_version")
    public int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    public InventorySnapshot state;

    @Column(name = "taken_at")
    public Instant takenAt;

    @Column(name = "taken_by")
    public String takenBy;
}
