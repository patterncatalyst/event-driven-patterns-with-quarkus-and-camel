-- A row trigger never fires for TRUNCATE; a statement trigger closes that gap.
-- The V1 function uses only TG_OP, so it serves a statement trigger as well.
CREATE TRIGGER order_event_no_truncate
    BEFORE TRUNCATE ON order_event
    FOR EACH STATEMENT EXECUTE FUNCTION order_event_append_only();
