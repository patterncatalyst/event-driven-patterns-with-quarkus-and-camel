-- Flyway owns the table; Hibernate validates its mapping against it. SKU-26 holds 1000 units. SKU-26-LATE is
-- absent on purpose: an order for it cannot be reserved until stock for it exists.
CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.inventory_item (
    sku              text PRIMARY KEY,
    quantity_on_hand integer NOT NULL CHECK (quantity_on_hand >= 0)
);

INSERT INTO inventory.inventory_item (sku, quantity_on_hand) VALUES ('SKU-26', 1000);
