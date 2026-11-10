"""One command, two storage outcomes: the row overwrites its status, the stream appends a fact."""

from generate_diagram import emit

emit(
    "01-row-update-vs-event-append",
    960,
    510,
    bands=[
        {"x": 20, "y": 170, "w": 450, "h": 285, "fill": "#fafafa", "label": "Row model: orders"},
        {"x": 490, "y": 170, "w": 450, "h": 285, "fill": "#fafafa", "label": "Event model: order_event"},
    ],
    nodes=[
        {"x": 280, "y": 40, "w": 400, "h": 70, "style": "ink",
         "lines": ["ChangeOrderStatus", "orderId O-1001, to SHIPPED"]},
        {"x": 50, "y": 205, "w": 390, "h": 60, "style": "box",
         "lines": ["Before: status PAID", "one row, id O-1001"]},
        {"x": 50, "y": 295, "w": 390, "h": 60, "style": "accent",
         "lines": ["UPDATE orders SET status = SHIPPED", "in place"]},
        {"x": 50, "y": 385, "w": 390, "h": 60, "style": "box",
         "lines": ["After: status SHIPPED", "PAID is gone"]},
        {"x": 520, "y": 205, "w": 390, "h": 60, "style": "box",
         "lines": ["Before: v1 OrderPlaced, v2 OrderPaid", "two facts in stream O-1001"]},
        {"x": 520, "y": 295, "w": 390, "h": 60, "style": "accent",
         "lines": ["INSERT version 3 OrderShipped", "unique (stream_id, version)"]},
        {"x": 520, "y": 385, "w": 390, "h": 60, "style": "box",
         "lines": ["After: v1, v2, v3", "every earlier status is still there"]},
    ],
    edges=[
        {"x1": 400, "y1": 110, "x2": 245, "y2": 170, "amber": True},
        {"x1": 560, "y1": 110, "x2": 715, "y2": 170, "amber": True},
        {"x1": 245, "y1": 265, "x2": 245, "y2": 295},
        {"x1": 245, "y1": 355, "x2": 245, "y2": 385},
        {"x1": 715, "y1": 265, "x2": 715, "y2": 295},
        {"x1": 715, "y1": 355, "x2": 715, "y2": 385},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One command, two storage outcomes"},
        {"x": 480, "y": 485, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The row forgets PAID; the stream keeps every status that came before."},
    ],
)
