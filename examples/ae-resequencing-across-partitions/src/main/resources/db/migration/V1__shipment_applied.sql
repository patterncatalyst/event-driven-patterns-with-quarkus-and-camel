-- One row per shipment event the service applied. The identity id is the order in which events were applied.
CREATE TABLE shipment_applied (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        text        NOT NULL,
    event_type      text        NOT NULL,
    sequence        int         NOT NULL,
    global_sequence bigint      NOT NULL,
    applied_at      timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX shipment_applied_order ON shipment_applied (order_id, id);
