-- The row model: one mutable row per Consignment, updated in place.
CREATE TABLE consignment (
    id          text PRIMARY KEY,
    origin      text NOT NULL,
    destination text NOT NULL,
    weight_kg   numeric(12, 3) NOT NULL CHECK (weight_kg > 0),
    carrier     text NOT NULL,
    status      text NOT NULL,
    version     bigint NOT NULL DEFAULT 0
);

-- The event model: an append-only stream per Consignment.
CREATE TABLE consignment_event (
    id          bigserial PRIMARY KEY,
    stream_id   text NOT NULL,
    version     int NOT NULL CHECK (version > 0),
    type        text NOT NULL,
    payload     jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT consignment_event_stream_version UNIQUE (stream_id, version)
);

CREATE FUNCTION consignment_event_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'consignment_event is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER consignment_event_no_update_delete
    BEFORE UPDATE OR DELETE ON consignment_event
    FOR EACH ROW EXECUTE FUNCTION consignment_event_append_only();
