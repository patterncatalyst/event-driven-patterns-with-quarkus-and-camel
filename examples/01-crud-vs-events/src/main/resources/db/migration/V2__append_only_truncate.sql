-- A row trigger never fires for TRUNCATE; a statement trigger closes that gap.
-- The V1 function uses only TG_OP, so it serves a statement trigger as well.
CREATE TRIGGER consignment_event_no_truncate
    BEFORE TRUNCATE ON consignment_event
    FOR EACH STATEMENT EXECUTE FUNCTION consignment_event_append_only();
