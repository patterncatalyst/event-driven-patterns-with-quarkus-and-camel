-- One row per order. The orchestrator that owns it is recorded, and so is the workflow instance when there is one,
-- so a step that only knows the instance can find its order. Flow's own tables come from its migration (V1.0.0).
CREATE TABLE orders.orders (
    id               TEXT PRIMARY KEY,
    customer_id      TEXT NOT NULL,
    item_sku         TEXT NOT NULL,
    quantity         INTEGER NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    status           TEXT NOT NULL CHECK (status IN ('PLACED', 'PAID', 'CANCELLED')),
    orchestrator     TEXT NOT NULL CHECK (orchestrator IN ('camel-lra', 'flow')),
    flow_instance_id TEXT
);
