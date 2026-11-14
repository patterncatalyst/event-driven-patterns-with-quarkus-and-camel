package com.patterncatalyst.shipping.saga.order;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The saga of one order: its state, the deadline of the step it waits in, and why it was cancelled. */
@Entity
@Table(name = "order_saga")
public class SagaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "order_id")
    public String orderId;
    @Enumerated(EnumType.STRING)
    public SagaState state;
    public Instant deadline;
    public int version;
    @Enumerated(EnumType.STRING)
    public CancelReason reason;
}
