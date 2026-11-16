"""Expand: the producer sends both orderId and orderRef, the v1 consumer reads orderId, the v2 consumer reads orderRef, and both store every record."""

from generate_diagram import emit

emit(
    "af-schema-expand",
    1000,
    340,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 230, "fill": "#fafafa", "label": "Expand: old and new fields side by side"},
    ],
    nodes=[
        {"x": 40, "y": 120, "w": 190, "h": 90, "style": "user", "lines": ["producer", "sends orderId", "and orderRef"]},
        {"x": 290, "y": 120, "w": 170, "h": 90, "style": "kernel", "lines": ["edp.orders.placed", "one topic, one shape"]},
        {"x": 530, "y": 95, "w": 200, "h": 60, "style": "box", "lines": ["v1 consumer", "reads orderId"]},
        {"x": 530, "y": 185, "w": 200, "h": 60, "style": "user", "lines": ["v2 consumer", "reads orderRef"]},
        {"x": 790, "y": 120, "w": 170, "h": 90, "style": "accent", "lines": ["Every record stored", "by both versions"]},
    ],
    edges=[
        {"x1": 230, "y1": 165, "x2": 290, "y2": 165},
        {"x1": 460, "y1": 150, "x2": 530, "y2": 125},
        {"x1": 460, "y1": 180, "x2": 530, "y2": 215},
        {"x1": 730, "y1": 125, "x2": 790, "y2": 150, "amber": True},
        {"x1": 730, "y1": 215, "x2": 790, "y2": 180, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "An additive change loses nothing"},
        {"x": 500, "y": 315, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The old consumer ignores the field it does not know"},
    ],
)
