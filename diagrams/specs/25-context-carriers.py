"""The five carriers one trace context rides on between a request and a consumer: a header, a column, a span id, a record header, a span parent."""

from generate_diagram import emit

emit(
    "25-context-carriers",
    1000,
    300,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 150, "fill": "#fafafa", "label": "One trace id, five carriers"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 150, "h": 80, "style": "user", "lines": ["POST /orders", "order-service", "server span"]},
        {"x": 230, "y": 95, "w": 150, "h": 80, "style": "box", "lines": ["Outbox row", "same transaction", "outbox-write span"]},
        {"x": 420, "y": 95, "w": 150, "h": 80, "style": "accent", "lines": ["Log and Connect", "db-log-write", "debezium-read"]},
        {"x": 610, "y": 95, "w": 150, "h": 80, "style": "ink", "lines": ["Kafka record", "edp.orders.placed", "one partition"]},
        {"x": 800, "y": 95, "w": 160, "h": 80, "style": "box", "lines": ["inventory-service", "consumer span", "reserve stock"]},
    ],
    edges=[
        {"x1": 190, "y1": 135, "x2": 230, "y2": 135, "amber": True},
        {"x1": 380, "y1": 135, "x2": 420, "y2": 135, "amber": True},
        {"x1": 570, "y1": 135, "x2": 610, "y2": 135, "amber": True},
        {"x1": 760, "y1": 135, "x2": 800, "y2": 135, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Every relay copies the context; the first hop that does not starts a new trace"},
        {"x": 115, "y": 235, "anchor": "middle", "size": 12, "color": "#555555", "text": "traceparent HTTP header"},
        {"x": 305, "y": 235, "anchor": "middle", "size": 12, "color": "#555555", "text": "tracingspancontext column"},
        {"x": 495, "y": 235, "anchor": "middle", "size": 12, "color": "#555555", "text": "span started from the log entry"},
        {"x": 685, "y": 235, "anchor": "middle", "size": 12, "color": "#555555", "text": "traceparent record header"},
        {"x": 880, "y": 235, "anchor": "middle", "size": 12, "color": "#555555", "text": "child of the span in the header"},
        {"x": 500, "y": 275, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Carrier under each hop: the place the context is written so the next hop can read it"},
    ],
)
