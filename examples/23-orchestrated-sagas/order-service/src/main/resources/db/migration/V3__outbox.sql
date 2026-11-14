-- The table the Debezium outbox extension maps (default name and columns, plus the topic column). Flyway owns the
-- schema; the connector reads the inserts from the transaction log and routes each row to its topic.
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
