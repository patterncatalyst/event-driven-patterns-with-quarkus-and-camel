"""A producer renames orderId to orderRef: the v1 consumer cannot store the record, the offset is committed anyway and the only trace is a log line."""

from generate_diagram import emit

emit(
    "af-schema-breaking",
    1000,
    340,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 230, "fill": "#fff8ef", "label": "Breaking change: orderId becomes orderRef"},
    ],
    nodes=[
        {"x": 40, "y": 120, "w": 170, "h": 90, "style": "user", "lines": ["v2 producer", "sends orderRef", "no orderId"]},
        {"x": 260, "y": 120, "w": 170, "h": 90, "style": "kernel", "lines": ["edp.orders.placed", "accepts the record"]},
        {"x": 480, "y": 120, "w": 200, "h": 90, "style": "box", "lines": ["v1 consumer", "insert fails: order_id", "is null"]},
        {"x": 740, "y": 95, "w": 220, "h": 60, "style": "accent", "lines": ["Offset committed", "group lag is 0"]},
        {"x": 740, "y": 185, "w": 220, "h": 60, "style": "ghost", "lines": ["No row exists", "ERROR Failed delivery in the log"]},
    ],
    edges=[
        {"x1": 210, "y1": 165, "x2": 260, "y2": 165},
        {"x1": 430, "y1": 165, "x2": 480, "y2": 165},
        {"x1": 680, "y1": 150, "x2": 740, "y2": 125, "amber": True},
        {"x1": 680, "y1": 180, "x2": 740, "y2": 215, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A breaking change is a silent loss"},
        {"x": 500, "y": 315, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "A dashboard on lag stays at zero; a schema registry with a compatibility rule would have refused the change"},
    ],
)
