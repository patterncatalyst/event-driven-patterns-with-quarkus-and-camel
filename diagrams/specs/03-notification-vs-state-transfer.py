"""The same OrderPlaced as a notification and as carried state, and what each consumer must do next."""

from generate_diagram import emit

emit(
    "03-notification-vs-state-transfer",
    1280,
    470,
    bands=[
        {"x": 20, "y": 50, "w": 1240, "h": 175, "fill": "#fafafa", "label": "Event notification: the event holds the id"},
        {"x": 20, "y": 240, "w": 1240, "h": 175, "fill": "#fafafa", "label": "Event-carried state transfer: the event holds the Order"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 250, "h": 80, "style": "box",
         "lines": ["OrderPlaced", "orderId O-1001"]},
        {"x": 350, "y": 100, "w": 250, "h": 80, "style": "box",
         "lines": ["Consumer", "needs the customer"]},
        {"x": 660, "y": 100, "w": 250, "h": 80, "style": "ink",
         "lines": ["GET /orders/O-1001", "order-service must answer"]},
        {"x": 970, "y": 100, "w": 250, "h": 80, "style": "box",
         "lines": ["Current state", "as of the fetch"]},
        {"x": 40, "y": 290, "w": 250, "h": 80, "style": "accent",
         "lines": ["OrderPlaced", "whole Order, status PLACED"]},
        {"x": 350, "y": 290, "w": 250, "h": 80, "style": "box",
         "lines": ["Consumer", "needs the customer"]},
        {"x": 660, "y": 290, "w": 250, "h": 80, "style": "accent",
         "lines": ["Local copy", "no request to order-service"]},
        {"x": 970, "y": 290, "w": 250, "h": 80, "style": "box",
         "lines": ["State of the last event", "as old as the lag"]},
    ],
    edges=[
        {"x1": 290, "y1": 140, "x2": 350, "y2": 140},
        {"x1": 600, "y1": 140, "x2": 660, "y2": 140},
        {"x1": 910, "y1": 140, "x2": 970, "y2": 140},
        {"x1": 290, "y1": 330, "x2": 350, "y2": 330, "amber": True},
        {"x1": 600, "y1": 330, "x2": 660, "y2": 330, "amber": True},
        {"x1": 910, "y1": 330, "x2": 970, "y2": 330, "amber": True},
    ],
    notes=[
        {"x": 640, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Notification or state transfer: who has to be up, and how fresh the answer is"},
        {"x": 640, "y": 445, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Notification keeps one owner of the data; state transfer trades freshness and size for independence."},
    ],
)
