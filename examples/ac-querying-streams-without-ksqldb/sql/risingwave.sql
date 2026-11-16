-- RisingWave: a source over the topic, and two materialized views over one-minute tumbling windows of event time.
-- The watermark delay is the allowed lateness (the placeholder is filled from LATENESS_SECONDS by verify.sh).
-- counts_rw updates while a window is open. counts_rw_final uses EMIT ON WINDOW CLOSE, which RisingWave
-- documents as an experimental feature, and holds a window only after it closes.
CREATE SOURCE delivered (
  "orderId" VARCHAR,
  carrier VARCHAR,
  "deliveredAt" TIMESTAMPTZ,
  WATERMARK FOR "deliveredAt" AS "deliveredAt" - INTERVAL '@LATENESS@ seconds'
) WITH (
  connector = 'kafka',
  topic = 'edp.shipping.delivered',
  properties.bootstrap.server = 'kafka:9094',
  scan.startup.mode = 'earliest'
) FORMAT PLAIN ENCODE JSON;

CREATE MATERIALIZED VIEW counts_rw AS
SELECT carrier, window_start, count(*) AS deliveries
FROM TUMBLE(delivered, "deliveredAt", INTERVAL '1 minute')
GROUP BY carrier, window_start;

CREATE MATERIALIZED VIEW counts_rw_final AS
SELECT carrier, window_start, count(*) AS deliveries
FROM TUMBLE(delivered, "deliveredAt", INTERVAL '1 minute')
GROUP BY carrier, window_start
EMIT ON WINDOW CLOSE;
