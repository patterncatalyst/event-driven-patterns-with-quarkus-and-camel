package com.patterncatalyst.shipping.eventnotification.notification;

import java.math.BigDecimal;
import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A notification and the order facts read from order-service when it was built. */
@Entity
@Table(name = "notification")
public class NotificationRow extends PanacheEntityBase {

    @Id
    public String id;
    @Column(name = "order_id")
    public String orderId;
    @Column(name = "customer_id")
    public String customerId;
    public BigDecimal amount;
    @Column(name = "order_status")
    public String orderStatus;
    @Column(name = "event_type")
    public String eventType;
    @Column(name = "created_at")
    public Instant createdAt;
}
