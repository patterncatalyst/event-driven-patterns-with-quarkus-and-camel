"""The same OrderPlaced as a notification and as carried state, and what each consumer must do next."""

from generate_diagram import emit

emit(
    "03-notification-vs-state-transfer",
    960,
    470,
    bands=[
        {"x": 20, "y": 50, "w": 920, "h": 175, "fill": "#fafafa", "label": "Event notification: the event holds the id"},
        {"x": 20, "y": 240, "w": 920, "h": 175, "fill": "#fafafa", "label": "Event-carried state transfer: the event holds the Order"},
    ],
    nodes=[
        {"x": 730, "y": 115, "w": 190, "h": 80, "style": "box",
         "lines": ["Current state", "as of the fetch"]},
        {"x": 500, "y": 115, "w": 190, "h": 80, "style": "accent",
         "lines": ["Local copy", "no request to order-service"]},
        {"x": 730, "y": 305, "w": 190, "h": 80, "style": "box",
         "lines": ["State of the last event", "as old as the lag"]},
    ],
    icons=[
        {"x": 40, "y": 95, "w": 190, "h": 110, "kind": "message", "label": "OrderPlaced: the order id only"},
        {"x": 270, "y": 95, "w": 190, "h": 110, "kind": "channel", "label": "edp.orders.placed"},
        {"x": 500, "y": 95, "w": 190, "h": 110, "kind": "enricher", "label": "fetch from order-service"},
        {"x": 40, "y": 285, "w": 190, "h": 110, "kind": "message", "label": "OrderPlaced: whole Order, status PLACED"},
        {"x": 270, "y": 285, "w": 190, "h": 110, "kind": "channel", "label": "edp.orders.placed"},
    ],
    edges=[
        {"x1": 230, "y1": 150, "x2": 270, "y2": 150},
        {"x1": 460, "y1": 150, "x2": 500, "y2": 150},
        {"x1": 690, "y1": 155, "x2": 730, "y2": 155},
        {"x1": 230, "y1": 340, "x2": 270, "y2": 340, "amber": True},
        {"x1": 460, "y1": 340, "x2": 500, "y2": 345, "amber": True},
        {"x1": 690, "y1": 345, "x2": 730, "y2": 345, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Notification or state transfer: who has to be up, and how fresh the answer is"},
        {"x": 480, "y": 445, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Notification keeps one owner of the data; state transfer trades freshness and size for independence."},
    ],
)
