"""Event notification: a thin message on a channel names the order; the consumer uses a content enricher to fetch the facts from the source."""

from generate_diagram import emit

emit(
    "02-fetch-back",
    1000,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 300, "h": 250, "fill": "#fafafa", "label": "order-service"},
        {"x": 350, "y": 60, "w": 240, "h": 250, "fill": "#fafafa", "label": "Kafka"},
        {"x": 620, "y": 60, "w": 360, "h": 250, "fill": "#fafafa", "label": "notification-service"},
    ],
    nodes=[
        {"x": 45, "y": 95, "w": 250, "h": 55, "style": "box",
         "lines": ["Commit the order", "orders row, status PLACED"]},
        {"x": 650, "y": 235, "w": 300, "h": 55, "style": "box",
         "lines": ["Store the Notification", "facts as read at fetch time"]},
    ],
    icons=[
        {"x": 55, "y": 175, "w": 230, "h": 110, "kind": "message", "label": "OrderPlaced: the order id only"},
        {"x": 370, "y": 150, "w": 200, "h": 110, "kind": "channel", "label": "edp.orders.placed"},
        {"x": 630, "y": 95, "w": 150, "h": 110, "kind": "endpoint", "label": "consume the record"},
        {"x": 800, "y": 95, "w": 160, "h": 110, "kind": "enricher", "label": "fetch the order"},
    ],
    edges=[
        {"x1": 170, "y1": 150, "x2": 170, "y2": 191},
        {"x1": 285, "y1": 205, "x2": 370, "y2": 205, "amber": True},
        {"x1": 570, "y1": 205, "x2": 630, "y2": 150, "amber": True},
        {"x1": 780, "y1": 150, "x2": 800, "y2": 150},
        {"x1": 815, "y1": 150, "x2": 170, "y2": 150, "amber": True, "label": "GET /orders/{id}", "ly": -8},
        {"x1": 170, "y1": 128, "x2": 815, "y2": 128, "dashed": True, "label": "200 JSON", "ly": -8},
        {"x1": 875, "y1": 205, "x2": 800, "y2": 235},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The event says what happened; a content enricher asks the source for the rest"},
        {"x": 500, "y": 335, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The fetch reads the order as it is now, which may differ from the order at event time."},
    ],
)
