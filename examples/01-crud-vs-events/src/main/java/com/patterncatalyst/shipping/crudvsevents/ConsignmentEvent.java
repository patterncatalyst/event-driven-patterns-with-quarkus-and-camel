package com.patterncatalyst.shipping.crudvsevents;

import java.time.Instant;
import java.util.Map;

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

/** One immutable fact in a Consignment's stream. */
@Entity
@Immutable
@Table(name = "consignment_event")
public class ConsignmentEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "stream_id")
    public String streamId;
    public int version;
    public String type;
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> payload;
    @Column(name = "occurred_at")
    public Instant occurredAt;
}
