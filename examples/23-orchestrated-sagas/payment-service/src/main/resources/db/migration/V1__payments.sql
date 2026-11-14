-- One row per order: the payment taken for it and what became of it.
CREATE TABLE payments.payment (
    order_id TEXT PRIMARY KEY,
    amount   NUMERIC(12, 2) NOT NULL,
    status   TEXT NOT NULL CHECK (status IN ('COMPLETED', 'FAILED', 'REFUNDED'))
);
