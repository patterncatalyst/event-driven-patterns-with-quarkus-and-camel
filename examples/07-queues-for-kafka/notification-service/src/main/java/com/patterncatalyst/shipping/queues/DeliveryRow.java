package com.patterncatalyst.shipping.queues;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One delivery attempt: which worker took which record, how many times Kafka had delivered it, and how it ended. */
@Entity
@Table(name = "delivery_log")
public class DeliveryRow extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_id")
    public String orderId;
    @Column(name = "customer_id")
    public String customerId;
    public String mode;
    public String worker;
    @Column(name = "record_partition")
    public int recordPartition;
    @Column(name = "record_offset")
    public long recordOffset;
    @Column(name = "delivery_count")
    public int deliveryCount;
    @Column(name = "started_at")
    public Instant startedAt;
    @Column(name = "finished_at")
    public Instant finishedAt;
    public String outcome;
}
