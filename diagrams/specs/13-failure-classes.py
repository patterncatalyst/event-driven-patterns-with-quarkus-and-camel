"""Three failure classes of one record: a router classifies each one, retriable goes back to the main channel, permanent goes to the dead letter channel."""

from generate_diagram import emit

emit(
    "13-failure-classes",
    960,
    580,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 130, "fill": "#fafafa", "label": "Never succeeds: unparseable or invalid"},
        {"x": 20, "y": 210, "w": 920, "h": 160, "fill": "#fff8ef", "label": "Can succeed later: the row is locked"},
        {"x": 20, "y": 390, "w": 920, "h": 160, "fill": "#fafafa", "label": "Still failing after the last retry"},
    ],
    nodes=[
        {"x": 710, "y": 255, "w": 190, "h": 80, "style": "box", "lines": ["stock reserved", "offset committed"]},
    ],
    icons=[
        {"x": 40, "y": 85, "w": 160, "h": 100, "kind": "message", "label": "truncated JSON, quantity 0"},
        {"x": 270, "y": 85, "w": 150, "h": 100, "kind": "router", "label": "unparseable, no retry"},
        {"x": 490, "y": 85, "w": 170, "h": 100, "kind": "channel", "label": "edp.orders.placed-dlq"},
        {"x": 40, "y": 235, "w": 160, "h": 100, "kind": "message", "label": "valid order, row locked"},
        {"x": 270, "y": 235, "w": 150, "h": 100, "kind": "router", "label": "lock conflict, retriable"},
        {"x": 490, "y": 235, "w": 170, "h": 100, "kind": "channel", "label": "retry after 1 s, new tx"},
        {"x": 40, "y": 415, "w": 160, "h": 100, "kind": "message", "label": "row locked too long"},
        {"x": 270, "y": 415, "w": 150, "h": 100, "kind": "router", "label": "retries exhausted"},
        {"x": 490, "y": 415, "w": 170, "h": 100, "kind": "channel", "label": "edp.orders.placed-dlq"},
    ],
    edges=[
        {"x1": 200, "y1": 135, "x2": 270, "y2": 135},
        {"x1": 420, "y1": 135, "x2": 490, "y2": 135},
        {"x1": 200, "y1": 285, "x2": 270, "y2": 285},
        {"x1": 420, "y1": 285, "x2": 490, "y2": 285},
        {"x1": 660, "y1": 285, "x2": 710, "y2": 295, "amber": True},
        {"x1": 575, "y1": 345, "x2": 345, "y2": 345, "dashed": True, "label": "each attempt, until it succeeds", "ly": 14},
        {"x1": 200, "y1": 465, "x2": 270, "y2": 465},
        {"x1": 420, "y1": 465, "x2": 490, "y2": 465},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Retry what can succeed later, dead letter what never will"},
    ],
)
