-- Bulk-loaded history: three streams of 1,000, 10,000 and 100,000 events, no snapshots.
-- Version 1 receives 100, every tenth event receives 10, the rest reserve 1.
-- The quantities on hand are 201, 1,101 and 10,101.
INSERT INTO inventory.events (event_id, stream_type, stream_id, version, type, payload, occurred_at)
SELECT gen_random_uuid(), 'InventoryItem', s.sku, v,
  CASE WHEN v = 1 OR v % 10 = 0 THEN 'StockReceived' ELSE 'StockReserved' END,
  CASE WHEN v = 1 THEN jsonb_build_object('kind', 'StockReceived', 'quantity', 100)
       WHEN v % 10 = 0 THEN jsonb_build_object('kind', 'StockReceived', 'quantity', 10)
       ELSE jsonb_build_object('kind', 'StockReserved', 'orderId', 'O-15' || lpad(v::text, 5, '0'), 'quantity', 1) END,
  now()
FROM (VALUES ('SKU-1501', 1000), ('SKU-1502', 10000), ('SKU-1503', 100000)) AS s(sku, n),
     generate_series(1, s.n) AS v;
