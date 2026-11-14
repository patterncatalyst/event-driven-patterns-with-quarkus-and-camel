package com.patterncatalyst.shipping.orchestration.inventory;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The stock taken for one order. */
@Entity
@Table(name = "reservation")
public class ReservationEntity extends PanacheEntityBase {

    @Id
    @Column(name = "order_id")
    public String orderId;

    public String sku;

    public int quantity;

    public String status;

    public static ReservationEntity of(String orderId, String sku, int quantity, String status) {
        ReservationEntity r = new ReservationEntity();
        r.orderId = orderId;
        r.sku = sku;
        r.quantity = quantity;
        r.status = status;
        return r;
    }
}
