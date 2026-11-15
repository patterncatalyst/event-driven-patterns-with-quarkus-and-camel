-- The connector writes one row here every second (heartbeat.action.query). The change is captured, so the slot's
-- confirmed position moves past write-ahead log that no captured table produced, such as the inventory updates
-- and checkpoints that follow an order. Without it the backlog gauge would never fall back after a stall.
CREATE TABLE orders.heartbeat (
    id   INTEGER PRIMARY KEY,
    beat TIMESTAMPTZ NOT NULL
);
