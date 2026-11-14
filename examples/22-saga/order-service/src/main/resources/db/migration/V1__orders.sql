-- The order, and the saga that carries it to PAID. order_saga is the one place the state of the saga lives.
CREATE TABLE orders.orders (
    id          text PRIMARY KEY,
    customer_id text NOT NULL,
    item_sku    text NOT NULL,
    quantity    int NOT NULL CHECK (quantity > 0),
    amount      numeric(12, 2) NOT NULL,
    status      text NOT NULL
);

-- One row per order. Every transition is a conditional update on state, so the version only counts the winners.
CREATE TABLE orders.order_saga (
    order_id text PRIMARY KEY REFERENCES orders.orders (id),
    state    text NOT NULL,
    deadline timestamptz NOT NULL,
    version  int NOT NULL DEFAULT 0,
    reason   text
);

-- The sweeper reads only the open sagas.
CREATE INDEX order_saga_open ON orders.order_saga (deadline) WHERE state IN ('AWAITING_STOCK', 'AWAITING_PAYMENT');

-- Event ids that have been applied (the inbox of the idempotent consumer).
CREATE TABLE orders.inbox (
    event_id    uuid PRIMARY KEY,
    received_at timestamptz NOT NULL DEFAULT now()
);

-- The outbox table of the Debezium extension, as in the transactional outbox example.
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
