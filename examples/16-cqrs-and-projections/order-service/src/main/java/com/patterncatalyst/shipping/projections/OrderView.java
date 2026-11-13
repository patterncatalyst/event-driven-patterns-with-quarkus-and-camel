package com.patterncatalyst.shipping.projections;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of the read model. Immutable here: the application never saves it. Only the projector writes the table,
 * with native upserts, because each column belongs to a different event.
 */
@Entity
@Immutable
@Table(name = "order_view")
public class OrderView extends PanacheEntityBase {

    @Id
    @Column(name = "order_id")
    public String orderId;

    @Column(name = "customer_id")
    public String customerId;

    @Column(name = "item_sku")
    public String itemSku;

    public BigDecimal amount;

    @Column(name = "order_status")
    public String orderStatus;

    @Column(name = "payment_status")
    public String paymentStatus;

    @Column(name = "shipment_status")
    public String shipmentStatus;

    public String carrier;

    @Column(name = "tracking_number")
    public String trackingNumber;

    @Column(name = "last_event_at")
    public Instant lastEventAt;

    /** The furthest fact the view holds: cancelled, else delivered, else shipped, else paid, else placed. */
    public OrderStatus status() {
        if ("CANCELLED".equals(orderStatus)) {
            return OrderStatus.CANCELLED;
        }
        if ("DELIVERED".equals(shipmentStatus)) {
            return OrderStatus.DELIVERED;
        }
        if ("SCHEDULED".equals(shipmentStatus) || "IN_TRANSIT".equals(shipmentStatus)) {
            return OrderStatus.SHIPPED;
        }
        if ("COMPLETED".equals(paymentStatus)) {
            return OrderStatus.PAID;
        }
        return OrderStatus.PLACED;
    }
}
