-- The reconciliation schema. The runner owns every migration; the worker reads and writes these tables and validates
-- its mapping against them.

CREATE TABLE run (
    id           text PRIMARY KEY,
    window_from  timestamptz NOT NULL,
    window_to    timestamptz NOT NULL,
    status       text        NOT NULL,
    priority     text,
    created_at   timestamptz NOT NULL,
    finished_at  timestamptz,
    traceparent  text
);

-- One live or finished run per window. A FAILED run does not hold its window: replay reuses the run, a new request may not.
CREATE UNIQUE INDEX run_window_live ON run (window_from, window_to) WHERE status <> 'FAILED';

CREATE TABLE step (
    run_id       text    NOT NULL REFERENCES run (id),
    name         text    NOT NULL,
    ordinal      integer NOT NULL,
    status       text    NOT NULL,
    attempt      integer NOT NULL,
    job_name     text,
    created_at   timestamptz,
    admitted_at  timestamptz,
    finished_at  timestamptz,
    PRIMARY KEY (run_id, name)
);

-- Every Job a step has had, one row per attempt.
CREATE TABLE step_job (
    run_id    text    NOT NULL,
    name      text    NOT NULL,
    attempt   integer NOT NULL,
    job_name  text    NOT NULL,
    PRIMARY KEY (run_id, name, attempt),
    FOREIGN KEY (run_id, name) REFERENCES step (run_id, name)
);

-- The offset ranges the plan step recorded, and the partition's progress.
CREATE TABLE run_partition (
    run_id          text    NOT NULL REFERENCES run (id),
    partition       integer NOT NULL,
    scheduled_from  bigint  NOT NULL,
    scheduled_to    bigint  NOT NULL,
    delivered_from  bigint  NOT NULL,
    delivered_to    bigint  NOT NULL,
    status          text    NOT NULL,
    attempts        integer NOT NULL DEFAULT 0,
    records_read    bigint  NOT NULL DEFAULT 0,
    completed_at    timestamptz,
    PRIMARY KEY (run_id, partition)
);

-- Written in the transaction that marks the partition DONE, so a repeated partition leaves the same rows.
CREATE TABLE partition_result (
    run_id     text    NOT NULL,
    partition  integer NOT NULL,
    carrier    text    NOT NULL,
    scheduled  bigint  NOT NULL,
    delivered  bigint  NOT NULL,
    open       bigint  NOT NULL,
    PRIMARY KEY (run_id, partition, carrier),
    FOREIGN KEY (run_id, partition) REFERENCES run_partition (run_id, partition)
);

CREATE TABLE carrier_total (
    run_id     text   NOT NULL REFERENCES run (id),
    carrier    text   NOT NULL,
    scheduled  bigint NOT NULL,
    delivered  bigint NOT NULL,
    open       bigint NOT NULL,
    PRIMARY KEY (run_id, carrier)
);
