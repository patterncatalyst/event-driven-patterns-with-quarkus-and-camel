package com.patterncatalyst.shipping.deadletter;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_item")
public class InventoryItemEntity extends PanacheEntityBase {

    @Id
    public String sku;

    @Column(name = "quantity_on_hand")
    public int quantityOnHand;
}
