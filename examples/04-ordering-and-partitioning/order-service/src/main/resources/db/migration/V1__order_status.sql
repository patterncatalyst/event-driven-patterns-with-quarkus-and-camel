-- The current status per order: every applied event overwrites it.
-- next_sequence is the per-order sequence number the buffer applies next.
CREATE TABLE order_status (
    order_id      text PRIMARY KEY,
    status        text NOT NULL,
    last_event    text NOT NULL,
    next_sequence int NOT NULL DEFAULT 1
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

-- Events held until their turn. A row is deleted in the transaction that applies it.
CREATE TABLE pending_shipment_event (
    id       bigserial PRIMARY KEY,
    order_id text NOT NULL,
    sequence int NOT NULL,
    payload  text NOT NULL,
    UNIQUE (order_id, sequence)
);
