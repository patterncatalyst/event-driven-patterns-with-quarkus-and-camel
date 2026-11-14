package com.patterncatalyst.shipping.orchestration.payment;

import java.math.BigDecimal;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The payment taken for one order. */
@Entity
@Table(name = "payment")
public class PaymentEntity extends PanacheEntityBase {

    @Id
    @Column(name = "order_id")
    public String orderId;

    public BigDecimal amount;

    public String status;
}
