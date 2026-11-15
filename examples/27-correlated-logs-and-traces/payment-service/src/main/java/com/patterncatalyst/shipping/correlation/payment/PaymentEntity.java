package com.patterncatalyst.shipping.correlation.payment;

import java.math.BigDecimal;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The payment taken, or declined, for one order. */
@Entity
@Table(name = "payment")
public class PaymentEntity extends PanacheEntityBase {

    @Id
    @Column(name = "order_id")
    public String orderId;
    @Column(name = "payment_id")
    public String paymentId;
    public BigDecimal amount;
    public String status;
}
