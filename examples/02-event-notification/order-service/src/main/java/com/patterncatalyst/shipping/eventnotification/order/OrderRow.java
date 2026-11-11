package com.patterncatalyst.shipping.eventnotification.order;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The current state of one Order, owned by order-service alone. */
@Entity
@Table(name = "orders")
public class OrderRow extends PanacheEntityBase {

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
}
