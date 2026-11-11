package com.patterncatalyst.shipping.tuning;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One notification per consumer group and Kafka record. A second delivery of the same record
 * increments {@code attempts} instead of adding a row, so duplicates are counted, not hidden.
 */
@Entity
@Table(name = "notification")
public class NotificationRow extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "group_id")
    public String groupId;
    public String topic;
    @Column(name = "kafka_partition")
    public int kafkaPartition;
    @Column(name = "kafka_offset")
    public long kafkaOffset;
    @Column(name = "order_id")
    public String orderId;
    @Column(name = "event_type")
    public String eventType;
    public int attempts;
    /** The consumer thread that wrote the row last. */
    public String consumer;
    /** The replica (Kafka client id) that wrote the row last. */
    public String replica;
    @Column(name = "event_time")
    public Instant eventTime;
    @Column(name = "processed_at")
    public Instant processedAt;
}
