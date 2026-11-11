CREATE TABLE orders.orders (
    id          TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL,
    item_sku    TEXT NOT NULL,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status      TEXT NOT NULL
);

-- The table the Debezium outbox extension maps (default name and columns, plus the topic column).
-- Hibernate does not create it: Flyway owns the schema.
CREATE TABLE orders.outboxevent (
    id                 UUID PRIMARY KEY,
    aggregatetype      VARCHAR(255) NOT NULL,
    aggregateid        VARCHAR(255) NOT NULL,
    type               VARCHAR(255) NOT NULL,
    "timestamp"        TIMESTAMPTZ NOT NULL,
    payload            VARCHAR(8000),
    tracingspancontext VARCHAR(256),
    topic              VARCHAR(255) NOT NULL
);
