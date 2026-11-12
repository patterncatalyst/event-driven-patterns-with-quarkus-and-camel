"""The inbox row and the stock decrement commit in one transaction; a repeated event id inserts nothing and is skipped."""

from generate_diagram import emit

emit(
    "11-inbox-same-transaction",
    960,
    480,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 150, "fill": "#fafafa", "label": "First delivery of an event id"},
        {"x": 20, "y": 250, "w": 920, "h": 150, "fill": "#fafafa", "label": "Second delivery of the same event id"},
    ],
    nodes=[
        {"x": 40, "y": 105, "w": 170, "h": 70, "style": "ink", "lines": ["edp.orders.placed", "header id = A"]},
        {"x": 250, "y": 105, "w": 200, "h": 70, "style": "box", "lines": ["INSERT INTO inbox", "ON CONFLICT DO NOTHING"]},
        {"x": 490, "y": 105, "w": 200, "h": 70, "style": "box", "lines": ["UPDATE inventory_item", "quantity - 3"]},
        {"x": 730, "y": 105, "w": 190, "h": 70, "style": "accent", "lines": ["commit, then ack", "one row, one decrement"]},
        {"x": 40, "y": 295, "w": 170, "h": 70, "style": "ink", "lines": ["edp.orders.placed", "header id = A"]},
        {"x": 250, "y": 295, "w": 200, "h": 70, "style": "box", "lines": ["INSERT INTO inbox", "ON CONFLICT DO NOTHING"]},
        {"x": 490, "y": 295, "w": 200, "h": 70, "style": "ghost", "lines": ["no decrement", "0 rows inserted"]},
        {"x": 730, "y": 295, "w": 190, "h": 70, "style": "accent", "lines": ["ack", "duplicate skipped"]},
    ],
    edges=[
        {"x1": 210, "y1": 140, "x2": 250, "y2": 140},
        {"x1": 450, "y1": 140, "x2": 490, "y2": 140, "label": "1 row"},
        {"x1": 690, "y1": 140, "x2": 730, "y2": 140},
        {"x1": 210, "y1": 330, "x2": 250, "y2": 330},
        {"x1": 450, "y1": 330, "x2": 490, "y2": 330, "dashed": True, "label": "0 rows"},
        {"x1": 690, "y1": 330, "x2": 730, "y2": 330},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The processed-id row and the effect are one transaction"},
        {"x": 480, "y": 440, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "If the decrement fails, the transaction rolls back and the inbox row goes with it"},
    ],
)
