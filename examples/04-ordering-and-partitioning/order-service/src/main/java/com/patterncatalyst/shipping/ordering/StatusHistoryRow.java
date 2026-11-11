package com.patterncatalyst.shipping.ordering;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One applied event, in the order it was applied. The id is an identity column, so it records arrival order. */
@Entity
@Table(name = "order_status_history")
public class StatusHistoryRow extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "order_id")
    public String orderId;
    @Column(name = "event_type")
    public String eventType;
    public int sequence;
    @Enumerated(EnumType.STRING)
    public OrderStatus status;
    public String replica;
}
