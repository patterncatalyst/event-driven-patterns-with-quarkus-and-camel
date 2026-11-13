package com.patterncatalyst.shipping.evolution;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Reads a stream in version order, upcasting every row to the current shape, and appends in the current shape. */
@ApplicationScoped
public class EventStore implements PanacheRepositoryBase<StoredEvent, Long> {

    private static final String STREAM_TYPE = "Order";

    /** An event in the current shape, with the schema_version its row was written in. */
    public record Loaded(OrderEvent event, int version, int storedSchemaVersion) {
    }

    @Inject
    Upcasters upcasters;

    @Inject
    ObjectMapper json;

    public List<Loaded> load(String streamId) {
        return list("streamId = ?1 order by version", streamId).stream().map(this::upcast).toList();
    }

    private Loaded upcast(StoredEvent row) {
        JsonNode current = upcasters.toCurrent(row.type, row.payload, row.schemaVersion);
        OrderEvent event = switch (row.type) {
            case "OrderPlaced" -> json.convertValue(current, OrderEvent.OrderPlaced.class);
            case "OrderCancelled" -> json.convertValue(current, OrderEvent.OrderCancelled.class);
            default -> throw new IllegalStateException("unknown event type " + row.type);
        };
        return new Loaded(event, row.version, row.schemaVersion);
    }

    /** Appends one event as the next version, written with the schema_version of the shape it is in. */
    public void append(String streamId, int version, String type, int schemaVersion, Object payload) {
        StoredEvent row = new StoredEvent();
        row.eventId = UUID.randomUUID();
        row.streamType = STREAM_TYPE;
        row.streamId = streamId;
        row.version = version;
        row.type = type;
        row.schemaVersion = (short) schemaVersion;
        row.payload = json.valueToTree(payload);
        row.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        persistAndFlush(row);
    }
}
