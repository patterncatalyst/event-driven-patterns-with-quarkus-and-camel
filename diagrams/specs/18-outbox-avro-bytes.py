"""The command's transaction serializes OrderPlaced through the registry, stores the bytes in the outbox, and the connector moves them unchanged."""

from generate_diagram import emit

emit(
    "18-outbox-avro-bytes",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 480, "h": 400, "fill": "#fff8ef", "label": "One transaction in order-service"},
        {"x": 520, "y": 60, "w": 420, "h": 400, "fill": "#fafafa", "label": "Out of the service"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 440, "h": 56, "style": "box", "lines": ["place order", "append OrderPlaced, schema_version 2, as JSON"]},
        {"x": 40, "y": 185, "w": 440, "h": 70, "style": "accent", "lines": ["AvroKafkaSerializer, topic edp.orders.placed", "registers the schema, or the registry refuses and everything rolls back"]},
        {"x": 40, "y": 285, "w": 440, "h": 56, "style": "kernel", "lines": ["orders.events row, JSON", "orders.outboxevent row, bytea: 0, schema id, Avro bytes"]},
        {"x": 40, "y": 370, "w": 440, "h": 56, "style": "box", "lines": ["commit", "event and outbox row together, or neither"]},
        {"x": 700, "y": 100, "w": 220, "h": 70, "style": "ghost", "lines": ["Apicurio Registry", "edp.orders.placed-value"]},
        {"x": 540, "y": 285, "w": 380, "h": 56, "style": "ink", "lines": ["Debezium Event Router", "BinaryDataConverter, bytes unchanged"]},
        {"x": 540, "y": 370, "w": 380, "h": 56, "style": "ink", "lines": ["edp.orders.placed", "a consumer resolves the schema id in the registry"]},
    ],
    edges=[
        {"x1": 260, "y1": 156, "x2": 260, "y2": 185},
        {"x1": 480, "y1": 215, "x2": 700, "y2": 150, "dashed": True},
        {"x1": 260, "y1": 255, "x2": 260, "y2": 285},
        {"x1": 260, "y1": 341, "x2": 260, "y2": 370},
        {"x1": 480, "y1": 398, "x2": 540, "y2": 313, "amber": True},
        {"x1": 730, "y1": 341, "x2": 730, "y2": 370},
        {"x1": 810, "y1": 370, "x2": 810, "y2": 170, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The schema is checked where the event is written, inside the transaction"},
        {"x": 480, "y": 485, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The schema id travels in the payload, so a consumer resolves it from the registry."},
    ],
)
