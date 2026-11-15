"""A timeout starts a second trace: the request trace ends at AWAITING_PAYMENT, the sweeper's trace begins at the deadline and links back, and both carry the same order id."""

from generate_diagram import emit

emit(
    "27-timeout-two-traces",
    1000,
    380,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 120, "fill": "#fff8ef", "label": "Trace 1, started by POST /orders"},
        {"x": 20, "y": 220, "w": 960, "h": 120, "fill": "#fafafa", "label": "Trace 2, started by the sweeper after the deadline"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 170, "h": 70, "style": "box", "lines": ["POST /orders", "saga stores", "the traceparent"]},
        {"x": 235, "y": 95, "w": 170, "h": 70, "style": "box", "lines": ["Connect relay", "inventory reserves"]},
        {"x": 430, "y": 95, "w": 170, "h": 70, "style": "box", "lines": ["AWAITING_PAYMENT", "payment-service", "does not answer"]},
        {"x": 40, "y": 255, "w": 170, "h": 70, "style": "accent", "lines": ["cancel expired saga", "root span, order.id", "link to trace 1"]},
        {"x": 235, "y": 255, "w": 170, "h": 70, "style": "box", "lines": ["outbox-write", "OrderCancelled"]},
        {"x": 430, "y": 255, "w": 170, "h": 70, "style": "box", "lines": ["Connect relay", "inventory releases"]},
        {"x": 700, "y": 160, "w": 260, "h": 100, "style": "user", "lines": ["Search by order id", "returns both traces", "the link joins them"]},
    ],
    edges=[
        {"x1": 210, "y1": 130, "x2": 235, "y2": 130},
        {"x1": 405, "y1": 130, "x2": 430, "y2": 130},
        {"x1": 210, "y1": 290, "x2": 235, "y2": 290},
        {"x1": 405, "y1": 290, "x2": 430, "y2": 290},
        {"x1": 125, "y1": 255, "x2": 125, "y2": 165, "amber": True, "dashed": True},
        {"x1": 700, "y1": 210, "x2": 600, "y2": 130},
        {"x1": 700, "y1": 210, "x2": 600, "y2": 290},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Work that a timer starts is a new trace, linked to the one it continues"},
        {"x": 500, "y": 365, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "A parent would stretch trace 1 across the whole timeout; a link keeps both short"},
    ],
)
