-- Flyway owns both tables; Hibernate validates its mapping against them.
CREATE SCHEMA IF NOT EXISTS inventory;

-- The event store: one row per event, one unique version per stream, never changed.
CREATE TABLE inventory.events (
    position    bigserial PRIMARY KEY,
    event_id    uuid NOT NULL UNIQUE,
    stream_type text NOT NULL,
    stream_id   text NOT NULL,
    version     integer NOT NULL CHECK (version > 0),
    type        text NOT NULL,
    payload     jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT events_stream_version UNIQUE (stream_id, version)
);

CREATE FUNCTION inventory.events_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'events is append-only: % refused', TG_OP;
END;
$$;
CREATE TRIGGER events_no_update_delete BEFORE UPDATE OR DELETE ON inventory.events
    FOR EACH ROW EXECUTE FUNCTION inventory.events_append_only();
CREATE TRIGGER events_no_truncate BEFORE TRUNCATE ON inventory.events
    FOR EACH STATEMENT EXECUTE FUNCTION inventory.events_append_only();

-- A snapshot is a cache of the fold at one version: the latest one per stream, replaceable and deletable.
-- It is not append-only. taken_by (inline or camel) is a diagnostic for the comparison.
CREATE TABLE inventory.snapshots (
    stream_id      text PRIMARY KEY,
    version        integer NOT NULL,
    schema_version integer NOT NULL,
    state          jsonb NOT NULL,
    taken_at       timestamptz NOT NULL,
    taken_by       text NOT NULL
);
