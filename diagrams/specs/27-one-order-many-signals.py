"""One order crossing four processes: every span carries order.id, every handler's log line carries orderId, and every log line carries the trace id, so Tempo and Loki answer the same question."""

from generate_diagram import emit

emit(
    "27-one-order-many-signals",
    1000,
    440,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 150, "fill": "#fff8ef", "label": "The saga of order O-2701, one trace"},
        {"x": 20, "y": 250, "w": 960, "h": 150, "fill": "#fafafa", "label": "grafana/otel-lgtm"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 90, "style": "box", "lines": ["order-service", "POST /orders", "order.id, orderId"]},
        {"x": 280, "y": 100, "w": 200, "h": 90, "style": "ghost", "lines": ["Connect", "db-log-write, debezium-read", "relays the trace context"]},
        {"x": 520, "y": 100, "w": 200, "h": 90, "style": "box", "lines": ["inventory-service", "handle event", "order.id, orderId"]},
        {"x": 760, "y": 100, "w": 200, "h": 90, "style": "box", "lines": ["payment-service", "route and handle event", "order.id, orderId"]},
        {"x": 150, "y": 290, "w": 300, "h": 90, "style": "accent", "lines": ["Tempo", "{ span.order.id = \"O-2701\" }", "one trace, four services"]},
        {"x": 550, "y": 290, "w": 300, "h": 90, "style": "accent", "lines": ["Loki", "| orderId = \"O-2701\"", "every line holds the trace id"]},
    ],
    edges=[
        {"x1": 240, "y1": 145, "x2": 280, "y2": 145},
        {"x1": 480, "y1": 145, "x2": 520, "y2": 145},
        {"x1": 720, "y1": 145, "x2": 760, "y2": 145},
        {"x1": 140, "y1": 190, "x2": 250, "y2": 290, "amber": True},
        {"x1": 620, "y1": 190, "x2": 450, "y2": 290, "amber": True},
        {"x1": 160, "y1": 190, "x2": 650, "y2": 290, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A business id on every span and in every handler's log line, a trace id on every log line"},
        {"x": 500, "y": 425, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "The trace context carries the order through Connect"},
    ],
)
