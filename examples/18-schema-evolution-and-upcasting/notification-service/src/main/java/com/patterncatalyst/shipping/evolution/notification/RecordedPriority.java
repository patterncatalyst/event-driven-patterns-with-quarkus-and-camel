package com.patterncatalyst.shipping.evolution.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The shipping priority the service recorded for an order, and the reader that recorded it. */
@Entity
@Table(name = "recorded_priority")
public class RecordedPriority {

    @Id
    @Column(name = "order_id")
    public String orderId;

    @Column(name = "shipping_priority")
    public String shippingPriority;

    public String via;

    public RecordedPriority() {
    }

    public RecordedPriority(String orderId, String shippingPriority, String via) {
        this.orderId = orderId;
        this.shippingPriority = shippingPriority;
        this.via = via;
    }
}
