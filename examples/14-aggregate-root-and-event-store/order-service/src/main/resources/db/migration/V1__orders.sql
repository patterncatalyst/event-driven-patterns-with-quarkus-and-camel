-- The event store: one append-only table, one row per event, a stream per order.
-- (stream_id, version) is the optimistic concurrency check: two writers that load the same version
-- cannot both append the next one.
CREATE TABLE orders.events (
    position    bigserial PRIMARY KEY,
    event_id    uuid NOT NULL UNIQUE,
    stream_type text NOT NULL,
    stream_id   text NOT NULL,
    version     int NOT NULL CHECK (version > 0),
    type        text NOT NULL,
    payload     jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT events_stream_version UNIQUE (stream_id, version)
);

CREATE FUNCTION orders.events_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'events is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER events_no_update_delete BEFORE UPDATE OR DELETE ON orders.events
    FOR EACH ROW EXECUTE FUNCTION orders.events_append_only();
CREATE TRIGGER events_no_truncate BEFORE TRUNCATE ON orders.events
    FOR EACH STATEMENT EXECUTE FUNCTION orders.events_append_only();

-- The outbox table of the Debezium extension, as in the transactional outbox example.
CREATE TABLE orders.outboxevent (
    id                 UUID PRIMARY KEY,
    aggregatetype      VARCHAR(255) NOT NULL,
    aggregateid        VARCHAR(255) NOT NULL,
    type               VARCHAR(255) NOT NULL,
    "timestamp"        TIMESTAMPTZ NOT NULL,
    payload            VARCHAR(8000),
    tracingspancontext VARCHAR(256),
    topic              VARCHAR(255) NOT NULL
);
