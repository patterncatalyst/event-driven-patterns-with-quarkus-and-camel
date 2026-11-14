CREATE TABLE inventory.inventory_item (
    sku              TEXT PRIMARY KEY,
    quantity_on_hand INTEGER NOT NULL CHECK (quantity_on_hand >= 0)
);

INSERT INTO inventory.inventory_item (sku, quantity_on_hand) VALUES ('SKU-23', 20), ('SKU-23-BENCH', 1000);

-- One row per order. A release that arrives before its reserve leaves a RELEASED row with no quantity, so the
-- reserve that follows finds the order already released and takes nothing: release and reserve commute.
CREATE TABLE inventory.reservation (
    order_id TEXT PRIMARY KEY,
    sku      TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    status   TEXT NOT NULL CHECK (status IN ('RESERVED', 'RELEASED'))
);
