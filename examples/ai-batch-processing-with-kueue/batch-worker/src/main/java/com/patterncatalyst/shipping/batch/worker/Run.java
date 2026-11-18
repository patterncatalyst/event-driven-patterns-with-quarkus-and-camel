package com.patterncatalyst.shipping.batch.worker;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The run's window. The runner writes it; the worker only reads it. */
@Entity
@Table(name = "run")
public class Run extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "window_from")
    public Instant windowFrom;

    @Column(name = "window_to")
    public Instant windowTo;
}
