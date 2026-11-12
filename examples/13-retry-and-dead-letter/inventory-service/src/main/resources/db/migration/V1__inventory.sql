-- Flyway owns the table; Hibernate validates its mapping against it. Five SKUs start with 100 units.
CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.inventory_item (
    sku              text PRIMARY KEY,
    quantity_on_hand integer NOT NULL CHECK (quantity_on_hand >= 0)
);

INSERT INTO inventory.inventory_item (sku, quantity_on_hand)
SELECT 'SKU-' || n, 100 FROM generate_series(11, 15) AS n;
