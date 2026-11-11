-- One row per delivery attempt. The identity id is the order in which attempts finished logging.
CREATE TABLE delivery_log (
    id               bigserial PRIMARY KEY,
    order_id         text        NOT NULL,
    customer_id      text        NOT NULL,
    mode             text        NOT NULL,   -- share or group
    worker           text        NOT NULL,
    record_partition int         NOT NULL,
    record_offset    bigint      NOT NULL,
    delivery_count   int         NOT NULL,   -- share: the broker's count; group: attempt number
    started_at       timestamptz NOT NULL,
    finished_at      timestamptz NOT NULL,
    outcome          text        NOT NULL    -- sent, failed (released or retried) or rejected
);
CREATE INDEX delivery_log_order ON delivery_log (order_id, id);
