CREATE TABLE orders (
    id          TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL,
    item_sku    TEXT NOT NULL,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    amount      NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status      TEXT NOT NULL
);
