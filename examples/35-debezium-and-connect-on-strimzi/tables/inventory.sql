-- The legacy tables of chapter 10, created as the application owner edp. The connector's filtered publication
-- needs table ownership, and psql through kubectl exec runs as the postgres superuser, hence SET ROLE.
SET ROLE edp;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE TABLE IF NOT EXISTS inventory.inventory_item (item_sku text PRIMARY KEY, qty_on_hand int NOT NULL, last_changed timestamptz NOT NULL DEFAULT now());
INSERT INTO inventory.inventory_item (item_sku, qty_on_hand) SELECT 'SKU-' || g, 100 FROM generate_series(1, 5) g ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS inventory.dbz_signal (id varchar(42) PRIMARY KEY, type varchar(32) NOT NULL, data varchar(2048));
