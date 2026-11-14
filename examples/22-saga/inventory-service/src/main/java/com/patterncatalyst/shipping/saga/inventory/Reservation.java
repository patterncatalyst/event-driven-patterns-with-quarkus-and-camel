package com.patterncatalyst.shipping.saga.inventory;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** What inventory knows about one order: the stock held for it, or that it was released or refused. */
@Entity
@Table(name = "reservation")
public class Reservation extends PanacheEntityBase {

    @Id
    @Column(name = "order_id")
    public String orderId;
    public String sku;
    public int quantity;
    public String state;
}
