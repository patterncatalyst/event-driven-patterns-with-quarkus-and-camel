"""From a log line to its trace and back: the trace_id field of the line opens the trace in Tempo, and the trace id queries Loki for every line of every service."""

from generate_diagram import emit

emit(
    "27-log-trace-roundtrip",
    1000,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 470, "h": 240, "fill": "#fafafa", "label": "Loki"},
        {"x": 510, "y": 60, "w": 470, "h": 240, "fill": "#fff8ef", "label": "Tempo"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 430, "h": 80, "style": "box", "lines": ["payment-service line", "orderId = O-2701, trace_id = 4bf9...", "payment for O-2701: COMPLETED"]},
        {"x": 40, "y": 205, "w": 430, "h": 80, "style": "box", "lines": ["lines of three services", "{service_name=~\".+\"} | trace_id = \"4bf9...\"", "order, inventory, payment"]},
        {"x": 530, "y": 100, "w": 430, "h": 80, "style": "accent", "lines": ["trace 4bf9...", "POST /orders, db-log-write,", "handle event, PaymentProcessed"]},
        {"x": 530, "y": 205, "w": 430, "h": 80, "style": "accent", "lines": ["span of payment-service", "service.name = payment-service", "order.id = O-2701"]},
    ],
    edges=[
        {"x1": 470, "y1": 140, "x2": 530, "y2": 140, "amber": True},
        {"x1": 530, "y1": 245, "x2": 470, "y2": 245, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The trace id is the key in both directions"},
        {"x": 250, "y": 325, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Loki derived field trace_id opens the trace"},
        {"x": 740, "y": 325, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Tempo trace-to-logs query filters Loki by trace_id"},
    ],
)
