-- Flyway runs in the orders schema (quarkus.flyway.schemas). The table is orders because order is reserved.
CREATE TABLE orders (
    id          text PRIMARY KEY,
    customer_id text NOT NULL,
    item_sku    text NOT NULL,
    quantity    int NOT NULL CHECK (quantity > 0),
    amount      numeric(12, 2) NOT NULL CHECK (amount > 0),
    status      text NOT NULL
);

-- The large part of an order, stored once. Events carry its id.
CREATE TABLE claim_check (
    id         uuid PRIMARY KEY,
    order_id   text NOT NULL REFERENCES orders (id),
    content    text NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX claim_check_order_id ON claim_check (order_id);
