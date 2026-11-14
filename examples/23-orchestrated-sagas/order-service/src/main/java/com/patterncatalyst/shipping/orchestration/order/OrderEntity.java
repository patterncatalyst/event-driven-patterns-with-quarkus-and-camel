package com.patterncatalyst.shipping.orchestration.order;

import java.math.BigDecimal;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An order and the orchestrator that owns its saga. */
@Entity
@Table(name = "orders")
public class OrderEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "customer_id")
    public String customerId;

    @Column(name = "item_sku")
    public String itemSku;

    public int quantity;

    public BigDecimal amount;

    public String status;

    public String orchestrator;

    @Column(name = "flow_instance_id")
    public String flowInstanceId;
}
