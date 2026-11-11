"""Event notification: the event names the order, the consumer fetches the facts from the source."""

from generate_diagram import emit

emit(
    "02-fetch-back",
    1010,
    500,
    bands=[
        {"x": 20, "y": 50, "w": 290, "h": 395, "fill": "#fafafa", "label": "order-service"},
        {"x": 360, "y": 50, "w": 290, "h": 395, "fill": "#fafafa", "label": "Kafka"},
        {"x": 700, "y": 50, "w": 290, "h": 395, "fill": "#fafafa", "label": "notification-service"},
    ],
    nodes=[
        {"x": 40, "y": 80, "w": 250, "h": 60, "style": "box",
         "lines": ["1. Commit the order", "orders row, status PLACED"]},
        {"x": 40, "y": 180, "w": 250, "h": 60, "style": "accent",
         "lines": ["2. Publish OrderPlaced", "after commit, order id only"]},
        {"x": 380, "y": 180, "w": 250, "h": 60, "style": "ink",
         "lines": ["edp.orders.placed", "key O-1001, value: the id"]},
        {"x": 720, "y": 180, "w": 250, "h": 60, "style": "box",
         "lines": ["3. Consume the record", "offset stays until step 5"]},
        {"x": 720, "y": 280, "w": 250, "h": 60, "style": "accent",
         "lines": ["4. Fetch the order", "Camel http, GET /orders/O-1001"]},
        {"x": 40, "y": 280, "w": 250, "h": 60, "style": "box",
         "lines": ["Serve the order", "customer, amount, status now"]},
        {"x": 720, "y": 375, "w": 250, "h": 50, "style": "box",
         "lines": ["5. Store the Notification", "facts as read at fetch time"]},
    ],
    edges=[
        {"x1": 165, "y1": 140, "x2": 165, "y2": 180},
        {"x1": 290, "y1": 210, "x2": 380, "y2": 210, "amber": True},
        {"x1": 630, "y1": 210, "x2": 720, "y2": 210, "amber": True},
        {"x1": 845, "y1": 240, "x2": 845, "y2": 280},
        {"x1": 720, "y1": 297, "x2": 290, "y2": 297, "amber": True, "label": "GET", "ly": -8},
        {"x1": 290, "y1": 325, "x2": 720, "y2": 325, "dashed": True, "label": "200 JSON", "ly": 14},
        {"x1": 845, "y1": 340, "x2": 845, "y2": 375},
    ],
    notes=[
        {"x": 505, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The event says what happened; the consumer asks the source for the rest"},
        {"x": 505, "y": 470, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The fetch reads the order as it is now, which may differ from the order at event time."},
    ],
)
