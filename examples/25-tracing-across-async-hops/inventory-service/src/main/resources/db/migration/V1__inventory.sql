CREATE TABLE inventory.inventory_item (
    sku              text PRIMARY KEY,
    quantity_on_hand int NOT NULL CHECK (quantity_on_hand >= 0)
);

-- SKU-25 is the stock of the walkthrough; SKU-25-BULK is large enough for the burst of orders.
INSERT INTO inventory.inventory_item (sku, quantity_on_hand) VALUES ('SKU-25', 10), ('SKU-25-BULK', 1000);
