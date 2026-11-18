package com.patterncatalyst.shipping.batch.runner;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One reconciliation of a window. */
@Entity
@Table(name = "run")
public class Run extends PanacheEntityBase {

    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Id
    public String id;

    @Column(name = "window_from")
    public Instant windowFrom;

    @Column(name = "window_to")
    public Instant windowTo;

    public String status;

    public String priority;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "finished_at")
    public Instant finishedAt;

    public String traceparent;
}
