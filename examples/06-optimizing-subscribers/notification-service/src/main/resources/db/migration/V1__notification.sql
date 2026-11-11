-- One row per consumer group and Kafka record. A repeated delivery increments attempts.
-- kafka_partition and kafka_offset avoid the SQL words partition and offset.
CREATE TABLE notification (
    id              bigserial PRIMARY KEY,
    group_id        text        NOT NULL,
    topic           text        NOT NULL,
    kafka_partition int         NOT NULL,
    kafka_offset    bigint      NOT NULL,
    order_id        text        NOT NULL,
    event_type      text        NOT NULL,
    attempts        int         NOT NULL DEFAULT 1,
    consumer        text        NOT NULL,
    replica         text        NOT NULL,
    event_time      timestamptz NOT NULL,
    processed_at    timestamptz NOT NULL,
    UNIQUE (group_id, topic, kafka_partition, kafka_offset)
);
CREATE INDEX notification_group ON notification (group_id);
