CREATE TABLE inventory.inventory_item (
    sku              text PRIMARY KEY,
    quantity_on_hand int NOT NULL CHECK (quantity_on_hand >= 0)
);

-- SKU-22 is the stock of the walkthrough; SKU-22-BULK is large enough for the burst of orders.
INSERT INTO inventory.inventory_item (sku, quantity_on_hand) VALUES ('SKU-22', 10), ('SKU-22-BULK', 1000);

-- One row per order. RESERVED holds stock. RELEASED is the compensation, and also what a release leaves when it
-- arrives before the reservation: the row that makes the later reservation refuse. INSUFFICIENT holds nothing.
CREATE TABLE inventory.reservation (
    order_id text PRIMARY KEY,
    sku      text,
    quantity int NOT NULL,
    state    text NOT NULL
);

-- Event ids that have been applied (the inbox of the idempotent consumer).
CREATE TABLE inventory.inbox (
    event_id    uuid PRIMARY KEY,
    received_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE inventory.outboxevent (
    id                 UUID PRIMARY KEY,
    aggregatetype      VARCHAR(255) NOT NULL,
    aggregateid        VARCHAR(255) NOT NULL,
    type               VARCHAR(255) NOT NULL,
    "timestamp"        TIMESTAMPTZ NOT NULL,
    payload            VARCHAR(8000),
    tracingspancontext VARCHAR(256),
    topic              VARCHAR(255) NOT NULL
);
