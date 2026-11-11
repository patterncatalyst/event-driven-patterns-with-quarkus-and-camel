package com.patterncatalyst.shipping.orderservice;

import java.math.BigDecimal;

import com.patterncatalyst.shipping.Order;
import com.patterncatalyst.shipping.OrderStatus;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The order-service's own record of an Order. Nobody else reads this table. */
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

    static OrderRow of(Order order, OrderStatus status) {
        OrderRow row = new OrderRow();
        row.id = order.id();
        row.customerId = order.customerId();
        row.itemSku = order.itemSku();
        row.quantity = order.quantity();
        row.amount = order.amount();
        row.status = status;
        return row;
    }

    Order toOrder() {
        return new Order(id, customerId, itemSku, quantity, amount);
    }
}
