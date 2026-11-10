-- A row trigger never fires for TRUNCATE; a statement trigger closes that gap.
CREATE FUNCTION consignment_event_no_truncate() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'consignment_event is append-only: % refused', TG_OP;
END;
$$;

CREATE TRIGGER consignment_event_no_truncate
    BEFORE TRUNCATE ON consignment_event
    FOR EACH STATEMENT EXECUTE FUNCTION consignment_event_no_truncate();
