-- The read model: one row per order, each column written by the service that owns the fact.
-- There is no events table and no write model in this schema.
CREATE TABLE orders.order_view (
    order_id        text PRIMARY KEY,
    customer_id     text,
    item_sku        text,
    amount          numeric(12,2),
    order_status    text,
    payment_status  text,
    shipment_status text,
    carrier         text,
    tracking_number text,
    last_event_at   timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX order_view_customer ON orders.order_view (customer_id);

-- Position of a shipment status in its lifecycle. The guarded upsert applies a shipment event only when it moves
-- the row forward.
CREATE FUNCTION orders.shipment_rank(s text) RETURNS int LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE s WHEN 'PENDING' THEN 0 WHEN 'SCHEDULED' THEN 1 WHEN 'IN_TRANSIT' THEN 2 WHEN 'DELIVERED' THEN 3 ELSE -1 END
$$;
