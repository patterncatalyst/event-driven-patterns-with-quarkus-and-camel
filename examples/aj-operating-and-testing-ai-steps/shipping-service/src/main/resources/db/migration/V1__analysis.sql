-- The analysis table is the exactly-once guard: MemoizedAnalysisService looks a row up by this key before calling
-- the model. It is append-only: a later prompt version or model digest writes a new row for the same exception id,
-- never overwrites an old one. seq is a plain insertion-order tiebreaker, never assigned from application code.
CREATE TABLE analysis.analysis (
    exception_id   text NOT NULL,
    prompt_version text NOT NULL,
    model_digest   text NOT NULL,
    seq            bigserial NOT NULL,
    order_id       text NOT NULL,
    shipment_id    text NOT NULL,
    carrier        text NOT NULL,
    attempt        int NOT NULL,
    reason         text NOT NULL,
    evidence       text NOT NULL,
    model          text NOT NULL,
    occurred_at    timestamptz NOT NULL,
    PRIMARY KEY (exception_id, prompt_version, model_digest),
    UNIQUE (seq)
);

-- The disposition currently on record for an exception: one row per exception id, rebuilt by the backfill endpoint
-- from the latest analysis row. The replay endpoint reads this table in its default mode and calls the model for
-- nothing.
CREATE TABLE analysis.disposition_snapshot (
    exception_id   text PRIMARY KEY,
    order_id       text NOT NULL,
    disposition    text NOT NULL,
    reason         text NOT NULL,
    attempt        int NOT NULL,
    prompt_version text NOT NULL,
    model_digest   text NOT NULL,
    updated_at     timestamptz NOT NULL
);
