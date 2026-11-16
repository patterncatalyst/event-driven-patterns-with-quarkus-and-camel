-- Flink SQL: deliveries per carrier per tumbling minute of event time. The watermark delay is the allowed lateness
-- (the placeholder is filled from LATENESS_SECONDS by verify.sh). The result is written to an upsert-kafka topic and
-- leaves once, when the window closes.
SET 'table.local-time-zone' = 'UTC';
SET 'parallelism.default' = '1';

CREATE TABLE delivered (
  orderId STRING,
  carrier STRING,
  deliveredAt TIMESTAMP_LTZ(3),
  WATERMARK FOR deliveredAt AS deliveredAt - INTERVAL '@LATENESS@' SECOND
) WITH (
  'connector' = 'kafka',
  'topic' = 'edp.shipping.delivered',
  'properties.bootstrap.servers' = 'kafka:9094',
  'properties.group.id' = 'flink-delivery-counts',
  'scan.startup.mode' = 'earliest-offset',
  'format' = 'json',
  'json.timestamp-format.standard' = 'ISO-8601'
);

CREATE TABLE counts (
  carrier STRING,
  window_start TIMESTAMP_LTZ(3),
  window_end TIMESTAMP_LTZ(3),
  deliveries BIGINT,
  PRIMARY KEY (carrier, window_start) NOT ENFORCED
) WITH (
  'connector' = 'upsert-kafka',
  'topic' = 'edp.shipping.delivery-counts-flink',
  'properties.bootstrap.servers' = 'kafka:9094',
  'key.format' = 'json',
  'value.format' = 'json'
);

INSERT INTO counts
SELECT carrier, window_start, window_end, COUNT(*)
FROM TABLE(TUMBLE(TABLE delivered, DESCRIPTOR(deliveredAt), INTERVAL '1' MINUTE))
GROUP BY carrier, window_start, window_end;
