"""A redriven dead letter: the record is written again with a new timestamp, the event time header stays, and the two ages part."""

from generate_diagram import emit

emit(
    "26-event-age-redrive",
    1000,
    360,
    nodes=[
        {"x": 40, "y": 80, "w": 190, "h": 90, "style": "box", "lines": ["edp.orders.placed", "record time = commit", "occurredAt = commit"]},
        {"x": 280, "y": 80, "w": 190, "h": 90, "style": "ghost", "lines": ["inventory-service", "fails: no stock row", "nack"]},
        {"x": 520, "y": 80, "w": 190, "h": 90, "style": "box", "lines": ["edp.orders.placed-dlq", "headers kept,", "waits for a fix"]},
        {"x": 760, "y": 80, "w": 200, "h": 90, "style": "accent", "lines": ["Redrive", "written again:", "new record time,", "same occurredAt"]},
        {"x": 520, "y": 230, "w": 190, "h": 90, "style": "box", "lines": ["Record age", "now minus record time", "reads small"]},
        {"x": 760, "y": 230, "w": 200, "h": 90, "style": "accent", "lines": ["Event age", "now minus occurredAt", "reads the wait"]},
    ],
    edges=[
        {"x1": 230, "y1": 125, "x2": 280, "y2": 125},
        {"x1": 470, "y1": 125, "x2": 520, "y2": 125, "dashed": True},
        {"x1": 710, "y1": 125, "x2": 760, "y2": 125, "amber": True},
        {"x1": 800, "y1": 170, "x2": 650, "y2": 230},
        {"x1": 860, "y1": 170, "x2": 860, "y2": 230, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The time stamped when the event happened survives a rewrite; the record timestamp does not"},
    ],
)
