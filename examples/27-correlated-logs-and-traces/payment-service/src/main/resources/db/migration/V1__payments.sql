-- One payment per order: the order id is the key, so a repeated InventoryReserved takes nothing a second time.
CREATE TABLE payments.payment (
    order_id   text PRIMARY KEY,
    payment_id text NOT NULL,
    amount     numeric(12, 2) NOT NULL,
    status     text NOT NULL
);

-- The orders payment-service has seen cancelled: the semantic lock a payment checks before it takes money.
CREATE TABLE payments.cancelled_order (
    order_id text PRIMARY KEY
);

CREATE TABLE payments.outboxevent (
    id                 UUID PRIMARY KEY,
    aggregatetype      VARCHAR(255) NOT NULL,
    aggregateid        VARCHAR(255) NOT NULL,
    type               VARCHAR(255) NOT NULL,
    "timestamp"        TIMESTAMPTZ NOT NULL,
    payload            VARCHAR(8000),
    tracingspancontext VARCHAR(256),
    topic              VARCHAR(255) NOT NULL
);
