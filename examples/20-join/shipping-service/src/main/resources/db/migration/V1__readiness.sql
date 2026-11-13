-- The tables Camel's PostgresAggregationRepository reads and writes: open groups in readiness, and the ids of
-- completed groups in readiness_completed (used when the repository is asked to remember completions).
CREATE TABLE readiness (
    id varchar(255) NOT NULL,
    exchange bytea NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT readiness_pk PRIMARY KEY (id)
);

CREATE TABLE readiness_completed (
    id varchar(255) NOT NULL,
    exchange bytea NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT readiness_completed_pk PRIMARY KEY (id)
);
