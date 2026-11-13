-- What the service recorded for each order it was told about, and which reader recorded it.
CREATE TABLE notifications.recorded_priority (
    order_id          text PRIMARY KEY,
    shipping_priority text NOT NULL,
    via               text NOT NULL
);
