package com.patterncatalyst.shipping.correlation.inventory;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stock on hand for one SKU. */
@Entity
@Table(name = "inventory_item")
public class StockItem extends PanacheEntityBase {

    @Id
    public String sku;

    @Column(name = "quantity_on_hand")
    public int quantityOnHand;

    public static StockItem of(String sku, int quantityOnHand) {
        StockItem item = new StockItem();
        item.sku = sku;
        item.quantityOnHand = quantityOnHand;
        return item;
    }
}
