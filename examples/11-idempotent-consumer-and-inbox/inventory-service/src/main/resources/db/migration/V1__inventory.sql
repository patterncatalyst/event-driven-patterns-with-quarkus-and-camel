CREATE TABLE inventory.inventory_item (
    sku              TEXT PRIMARY KEY,
    quantity_on_hand INTEGER NOT NULL CHECK (quantity_on_hand >= 0)
);

INSERT INTO inventory.inventory_item (sku, quantity_on_hand) VALUES ('SKU-1', 100);

-- One row per event id that has been applied. The effect and this row commit together.
CREATE TABLE inventory.inbox (
    event_id    UUID PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The table Camel's JpaMessageIdRepository maps (its default entity, MessageProcessed) and its id sequence.
-- Hibernate does not create it: Flyway owns the schema.
CREATE TABLE inventory.camel_messageprocessed (
    id            BIGINT PRIMARY KEY,
    processorName VARCHAR(255),
    messageId     VARCHAR(255),
    createdAt     TIMESTAMP,
    UNIQUE (processorName, messageId)
);

CREATE SEQUENCE inventory.camel_messageprocessed_seq START 1 INCREMENT 50;
