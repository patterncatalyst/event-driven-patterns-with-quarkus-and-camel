-- One row per change event received. The legacy inventory_item table itself belongs to the legacy system.
CREATE TABLE item_change (
    id          bigserial PRIMARY KEY,
    op          text        NOT NULL,   -- r (snapshot read), c, u or d
    sku         text        NOT NULL,
    qty         int         NOT NULL,
    lsn         bigint      NOT NULL,   -- source.lsn of the event
    received_at timestamptz NOT NULL
);
CREATE INDEX item_change_sku ON item_change (sku, id);
