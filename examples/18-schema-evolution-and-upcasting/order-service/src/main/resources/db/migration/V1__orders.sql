-- The event store of the aggregate example, plus schema_version: the version of the payload shape the row was
-- written in. A row keeps the version it was written in for good; the code upcasts it when it reads it.
CREATE TABLE orders.events (
    position       bigserial PRIMARY KEY,
    event_id       uuid NOT NULL UNIQUE,
    stream_type    text NOT NULL,
    stream_id      text NOT NULL,
    version        int NOT NULL CHECK (version > 0),
    type           text NOT NULL,
    schema_version smallint NOT NULL CHECK (schema_version > 0),
    payload        jsonb NOT NULL,
    occurred_at    timestamptz NOT NULL,
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

-- The outbox table of the Debezium extension with a binary payload: the Avro bytes, schema id first.
CREATE TABLE orders.outboxevent (
    id                 UUID PRIMARY KEY,
    aggregatetype      VARCHAR(255) NOT NULL,
    aggregateid        VARCHAR(255) NOT NULL,
    type               VARCHAR(255) NOT NULL,
    "timestamp"        TIMESTAMPTZ NOT NULL,
    payload            bytea,
    tracingspancontext VARCHAR(256),
    topic              VARCHAR(255) NOT NULL
);
