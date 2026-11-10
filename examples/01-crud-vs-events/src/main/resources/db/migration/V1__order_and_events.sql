-- The row model: one mutable row per Order, updated in place.
-- The table is orders because order is a reserved word.
CREATE TABLE orders (
    id          text PRIMARY KEY,
    customer_id text NOT NULL,
    item_sku    text NOT NULL,
    quantity    int NOT NULL CHECK (quantity > 0),
    amount      numeric(12, 2) NOT NULL CHECK (amount > 0),
    status      text NOT NULL,
    version     bigint NOT NULL DEFAULT 0
);

-- The event model: an append-only stream per Order.
CREATE TABLE order_event (
    id          bigserial PRIMARY KEY,
    stream_id   text NOT NULL,
    version     int NOT NULL CHECK (version > 0),
    type        text NOT NULL,
    payload     jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    CONSTRAINT order_event_stream_version UNIQUE (stream_id, version)
);

CREATE FUNCTION order_event_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'order_event is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER order_event_no_update_delete
    BEFORE UPDATE OR DELETE ON order_event
    FOR EACH ROW EXECUTE FUNCTION order_event_append_only();
