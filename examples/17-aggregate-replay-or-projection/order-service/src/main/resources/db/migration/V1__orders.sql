-- The event store of chapter 14 (one append-only table, a stream per order, a unique (stream_id, version)), plus
-- tx_id: the transaction that wrote the row. The projector uses it to know which positions can still appear.
CREATE TABLE orders.events (
    position    bigserial PRIMARY KEY,
    event_id    uuid NOT NULL UNIQUE,
    stream_type text NOT NULL,
    stream_id   text NOT NULL,
    version     int NOT NULL CHECK (version > 0),
    type        text NOT NULL,
    payload     jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    tx_id       xid8 NOT NULL DEFAULT pg_current_xact_id(),
    CONSTRAINT events_stream_version UNIQUE (stream_id, version)
);

CREATE INDEX events_tx_position ON orders.events (tx_id, position);

CREATE FUNCTION orders.events_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'events is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER events_no_update_delete BEFORE UPDATE OR DELETE ON orders.events
    FOR EACH ROW EXECUTE FUNCTION orders.events_append_only();
CREATE TRIGGER events_no_truncate BEFORE TRUNCATE ON orders.events
    FOR EACH STATEMENT EXECUTE FUNCTION orders.events_append_only();

-- The projection is a derived cache, not a record: it is not append-only and can be dropped and rebuilt.
CREATE TABLE orders.carrier_daily_shipments (
    carrier   text NOT NULL CHECK (carrier IN ('FASTLINE', 'AIRCO')),
    day       date NOT NULL,
    shipments bigint NOT NULL,
    PRIMARY KEY (carrier, day)
);

-- Where the projector stopped: the last (tx_id, position) it applied. tx_id is the xid8 value as a bigint.
CREATE TABLE orders.projection_checkpoint (
    name          text PRIMARY KEY,
    last_tx_id    bigint NOT NULL,
    last_position bigint NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

INSERT INTO orders.projection_checkpoint (name, last_tx_id, last_position) VALUES ('carrier-daily', 0, 0);
