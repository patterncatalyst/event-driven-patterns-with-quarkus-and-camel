package com.patterncatalyst.shipping.notificationservice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** This service's local copy of an Order, kept up to date by the events and answered without calling the source. */
@Entity
@Table(name = "order_copy")
public class OrderCopy extends PanacheEntityBase {

    @Id
    public String id;
    @Column(name = "customer_id")
    public String customerId;
    @Column(name = "item_sku")
    public String itemSku;
    public int quantity;
    public BigDecimal amount;
    @Enumerated(EnumType.STRING)
    public OrderStatus status;
    @Column(name = "attachment_claim_id")
    public UUID attachmentClaimId;
    @Column(name = "attachment_size")
    public Long attachmentSize;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
