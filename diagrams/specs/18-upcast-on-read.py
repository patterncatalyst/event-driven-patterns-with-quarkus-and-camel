"""Stored rows keep the shape they were written in; the upcaster chain lifts a copy to the current shape when it is read."""

from generate_diagram import emit

emit(
    "18-upcast-on-read",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 330, "h": 400, "fill": "#fafafa", "label": "orders.events, append-only"},
        {"x": 380, "y": 60, "w": 560, "h": 400, "fill": "#fff8ef", "label": "A load in order-service"},
    ],
    nodes=[
        {"x": 45, "y": 100, "w": 280, "h": 70, "style": "ink", "lines": ["O-1801, schema_version 1", "OrderPlaced without shippingPriority"]},
        {"x": 45, "y": 200, "w": 280, "h": 70, "style": "ink", "lines": ["O-1803, schema_version 2", "OrderPlaced, shippingPriority EXPRESS"]},
        {"x": 45, "y": 330, "w": 280, "h": 90, "style": "ghost", "lines": ["UPDATE or DELETE", "refused by the trigger:", "events is append-only"]},
        {"x": 410, "y": 100, "w": 250, "h": 70, "style": "box", "lines": ["copy the JSON payload", "read schema_version"]},
        {"x": 410, "y": 200, "w": 250, "h": 70, "style": "accent", "lines": ["Upcasters chain", "1 to 2: add shippingPriority STANDARD"]},
        {"x": 690, "y": 200, "w": 220, "h": 70, "style": "box", "lines": ["OrderEvent.OrderPlaced", "current record, required field"]},
        {"x": 690, "y": 100, "w": 220, "h": 70, "style": "ghost", "lines": ["version 2 row", "no step, passes through"]},
        {"x": 550, "y": 330, "w": 360, "h": 70, "style": "kernel", "lines": ["OrderAggregate.rehydrate", "sees one shape, whatever was stored"]},
    ],
    edges=[
        {"x1": 325, "y1": 135, "x2": 410, "y2": 135},
        {"x1": 325, "y1": 235, "x2": 410, "y2": 150, "dashed": True},
        {"x1": 535, "y1": 170, "x2": 535, "y2": 200},
        {"x1": 660, "y1": 235, "x2": 690, "y2": 235, "amber": True},
        {"x1": 660, "y1": 135, "x2": 690, "y2": 135, "dashed": True},
        {"x1": 800, "y1": 170, "x2": 800, "y2": 200, "dashed": True},
        {"x1": 800, "y1": 270, "x2": 740, "y2": 330, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A stored event is never rewritten; a copy of it is upcast on every read"},
        {"x": 480, "y": 485, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Without the chain a version 1 row has no shippingPriority and the load fails."},
    ],
)
