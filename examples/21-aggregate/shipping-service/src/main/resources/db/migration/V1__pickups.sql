-- The tables Camel's PostgresAggregationRepository expects: open batches, and completed batches it keeps for recovery.
CREATE TABLE shipping.pickups (
    id       varchar(255) NOT NULL,
    exchange bytea        NOT NULL,
    version  bigint       NOT NULL,
    CONSTRAINT pickups_pk PRIMARY KEY (id)
);

CREATE TABLE shipping.pickups_completed (
    id       varchar(255) NOT NULL,
    exchange bytea        NOT NULL,
    version  bigint       NOT NULL,
    CONSTRAINT pickups_completed_pk PRIMARY KEY (id)
);
