CREATE TABLE notification (
    id           TEXT PRIMARY KEY,
    order_id     TEXT NOT NULL,
    customer_id  TEXT NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    order_status TEXT NOT NULL,
    event_type   TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX notification_order_id ON notification (order_id);
