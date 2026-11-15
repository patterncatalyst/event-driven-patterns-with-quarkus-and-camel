"""Two shapes for the consumer's span: a child in the order's trace, or the root of its own trace with a link back."""

from generate_diagram import emit

emit(
    "25-parent-or-link",
    1000,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 470, "h": 250, "fill": "#fafafa", "label": "Parent: one record, one trace"},
        {"x": 510, "y": 60, "w": 470, "h": 250, "fill": "#fafafa", "label": "Link: many records or later work"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 200, "h": 60, "style": "user", "lines": ["POST /orders", "trace A"]},
        {"x": 40, "y": 185, "w": 200, "h": 60, "style": "accent", "lines": ["db-log-write", "trace A"]},
        {"x": 270, "y": 185, "w": 200, "h": 60, "style": "box", "lines": ["consumer span", "trace A, child of db-log-write"]},
        {"x": 530, "y": 95, "w": 200, "h": 60, "style": "user", "lines": ["POST /orders", "trace A"]},
        {"x": 530, "y": 185, "w": 200, "h": 60, "style": "accent", "lines": ["db-log-write", "trace A ends here"]},
        {"x": 760, "y": 185, "w": 200, "h": 60, "style": "box", "lines": ["process span, no parent", "trace B"]},
    ],
    edges=[
        {"x1": 140, "y1": 155, "x2": 140, "y2": 185, "amber": True},
        {"x1": 240, "y1": 215, "x2": 270, "y2": 215, "amber": True},
        {"x1": 630, "y1": 155, "x2": 630, "y2": 185, "amber": True},
        {"x1": 760, "y1": 215, "x2": 730, "y2": 215, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The record carries the same context in both; only the consumer decides what it means"},
        {"x": 255, "y": 275, "anchor": "middle", "size": 12.5, "color": "#555555", "text": "Tempo shows one waterfall"},
        {"x": 745, "y": 275, "anchor": "middle", "size": 12.5, "color": "#555555", "text": "Tempo shows two traces; the link joins them"},
        {"x": 500, "y": 340, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "A dashed arrow is a span link: a reference to a span in another trace, not a parent"},
    ],
)
