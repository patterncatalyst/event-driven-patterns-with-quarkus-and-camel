-- Flyway runs in the notifications schema (quarkus.flyway.schemas).
-- A local copy of an Order, updated from OrderPlaced and OrderCancelled and never from a call.
CREATE TABLE order_copy (
    id                  text PRIMARY KEY,
    customer_id         text NOT NULL,
    item_sku            text NOT NULL,
    quantity            int NOT NULL,
    amount              numeric(12, 2) NOT NULL,
    status              text NOT NULL,
    attachment_claim_id uuid,
    attachment_size     bigint,
    updated_at          timestamptz NOT NULL
);
