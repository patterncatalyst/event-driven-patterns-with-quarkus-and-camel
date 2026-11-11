-- The current status per order: every applied event overwrites it.
CREATE TABLE order_status (
    order_id   text PRIMARY KEY,
    status     text NOT NULL,
    last_event text NOT NULL
);

-- One row per applied event; the identity id is the order in which events were applied.
CREATE TABLE order_status_history (
    id         bigserial PRIMARY KEY,
    order_id   text NOT NULL,
    event_type text NOT NULL,
    sequence   int NOT NULL,
    status     text NOT NULL,
    replica    text NOT NULL
);
CREATE INDEX order_status_history_order ON order_status_history (order_id, id);
