package com.patterncatalyst.shipping.ordering;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The current status of an order. Every applied event overwrites it: last write wins. {@code nextSequence} is the sequence number the buffer applies next. */
@Entity
@Table(name = "order_status")
public class OrderStatusRow extends PanacheEntityBase {

    @Id
    @Column(name = "order_id")
    public String orderId;
    @Enumerated(EnumType.STRING)
    public OrderStatus status;
    @Column(name = "last_event")
    public String lastEvent;
    @Column(name = "next_sequence")
    public int nextSequence = 1;
}
