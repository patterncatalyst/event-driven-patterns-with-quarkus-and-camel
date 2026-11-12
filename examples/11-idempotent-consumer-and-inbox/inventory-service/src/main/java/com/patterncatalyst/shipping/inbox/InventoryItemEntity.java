package com.patterncatalyst.shipping.inbox;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stock on hand for one SKU. */
@Entity
@Table(name = "inventory_item")
public class InventoryItemEntity extends PanacheEntityBase {

    @Id
    public String sku;

    @Column(name = "quantity_on_hand")
    public int quantityOnHand;

    public static InventoryItemEntity of(String sku, int quantityOnHand) {
        InventoryItemEntity item = new InventoryItemEntity();
        item.sku = sku;
        item.quantityOnHand = quantityOnHand;
        return item;
    }
}
