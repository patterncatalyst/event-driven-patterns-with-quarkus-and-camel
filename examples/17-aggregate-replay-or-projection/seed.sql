-- Bulk-loaded history for the orders :lo to :hi (psql -v lo=1 -v hi=1000), written as one transaction. Order ids are
-- O-17 followed by the number padded to six digits. Every order is placed and paid; then
--   90% are shipped, 60% FASTLINE and 40% AIRCO, on one of 30 UTC days ending today;
--   60% are delivered, which includes shipped orders only;
--   10% are cancelled instead of shipped.
-- That is 3.6 events per order.
BEGIN;
CREATE TEMP TABLE seeded AS
SELECT n, 'O-17' || lpad(n::text, 6, '0') AS id FROM generate_series(:lo, :hi) AS n;

INSERT INTO orders.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
SELECT gen_random_uuid(), 'Order', id, 1, 'OrderPlaced', jsonb_build_object('kind', 'OrderPlaced', 'orderId', id), now()
FROM seeded;

INSERT INTO orders.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
SELECT gen_random_uuid(), 'Order', id, 2, 'OrderPaid', jsonb_build_object('kind', 'OrderPaid', 'paymentId', 'P-' || id), now()
FROM seeded;

INSERT INTO orders.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
SELECT gen_random_uuid(), 'Order', id, 3, 'OrderShipped',
       jsonb_build_object('kind', 'OrderShipped', 'shipmentId', 'S-' || id,
                          'carrier', CASE WHEN n % 5 < 3 THEN 'FASTLINE' ELSE 'AIRCO' END),
       ((now() AT TIME ZONE 'UTC')::date - (n % 30))::timestamp AT TIME ZONE 'UTC' + interval '10 hours'
FROM seeded WHERE n % 10 <> 0;

INSERT INTO orders.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
SELECT gen_random_uuid(), 'Order', id, 3, 'OrderCancelled',
       jsonb_build_object('kind', 'OrderCancelled', 'reason', 'changed mind'), now()
FROM seeded WHERE n % 10 = 0;

INSERT INTO orders.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
SELECT gen_random_uuid(), 'Order', id, 4, 'OrderDelivered', jsonb_build_object('kind', 'OrderDelivered'), now()
FROM seeded WHERE n % 10 BETWEEN 1 AND 6;
COMMIT;
