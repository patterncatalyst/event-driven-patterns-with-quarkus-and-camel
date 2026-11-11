"""State carried in the event, a local copy in the consumer, and the copy answering while the source is down."""

from generate_diagram import emit

emit(
    "03-local-copy",
    1280,
    440,
    bands=[
        {"x": 20, "y": 50, "w": 1240, "h": 150, "fill": "#fafafa", "label": "Write path: the event carries the Order and its status"},
        {"x": 20, "y": 215, "w": 1240, "h": 170, "fill": "#fafafa", "label": "Read path: order-service stopped"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 250, "h": 80, "style": "ink",
         "lines": ["order-service", "POST /orders, after commit"]},
        {"x": 350, "y": 95, "w": 250, "h": 80, "style": "accent",
         "lines": ["edp.orders.placed", "key O-1001, whole Order, PLACED"]},
        {"x": 660, "y": 95, "w": 250, "h": 80, "style": "box",
         "lines": ["notification-service", "one route per topic"]},
        {"x": 970, "y": 95, "w": 250, "h": 80, "style": "accent",
         "lines": ["notifications.order_copy", "upsert by order id"]},
        {"x": 40, "y": 260, "w": 250, "h": 80, "style": "ghost",
         "lines": ["order-service", "stopped"]},
        {"x": 350, "y": 260, "w": 250, "h": 80, "style": "box",
         "lines": ["Caller", "GET /order-copies/O-1001"]},
        {"x": 660, "y": 260, "w": 250, "h": 80, "style": "box",
         "lines": ["notification-service", "reads its own table"]},
        {"x": 970, "y": 260, "w": 250, "h": 80, "style": "accent",
         "lines": ["200, every field", "a cancelled order shows CANCELLED"]},
    ],
    edges=[
        {"x1": 290, "y1": 135, "x2": 350, "y2": 135, "amber": True},
        {"x1": 600, "y1": 135, "x2": 660, "y2": 135, "amber": True},
        {"x1": 910, "y1": 135, "x2": 970, "y2": 135},
        {"x1": 600, "y1": 300, "x2": 660, "y2": 300},
        {"x1": 910, "y1": 300, "x2": 970, "y2": 300, "amber": True},
    ],
    notes=[
        {"x": 165, "y": 362, "anchor": "middle", "size": 11, "color": "#555555", "text": "receives no request"},
        {"x": 640, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "State carried in the event, answered from a local copy"},
        {"x": 640, "y": 415, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The copy answers without a request to the source; its freshness is the lag of the last event."},
    ],
)
