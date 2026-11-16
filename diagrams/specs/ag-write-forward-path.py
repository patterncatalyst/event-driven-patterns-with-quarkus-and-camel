"""The request writes the order and the outbox row in one transaction; the log carries the event out, a Camel route applies it to Redis behind a version guard, and the read endpoint answers from Redis alone."""

from generate_diagram import emit

emit(
    "ag-write-forward-path",
    1000,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 300, "h": 370, "fill": "#fff8ef", "label": "One transaction in order-service"},
        {"x": 350, "y": 60, "w": 300, "h": 370, "fill": "#fafafa", "label": "Out of the database"},
        {"x": 680, "y": 60, "w": 300, "h": 370, "fill": "#fafafa", "label": "Into the cache"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 260, "h": 60, "style": "box", "lines": ["POST /orders", "no Redis call"]},
        {"x": 40, "y": 200, "w": 260, "h": 70, "style": "accent", "lines": ["orders row, version n", "outbox row, full state", "commit together or not at all"]},
        {"x": 40, "y": 320, "w": 260, "h": 70, "style": "user", "lines": ["GET /orders/{id}", "reads Redis only", "a miss is a 404"]},
        {"x": 370, "y": 100, "w": 260, "h": 70, "style": "box", "lines": ["Debezium and EventRouter", "log tail, topic column routes"]},
        {"x": 370, "y": 230, "w": 260, "h": 70, "style": "ink", "lines": ["edp.orders.placed", "edp.orders.cancelled", "key = order id, value = full state"]},
        {"x": 700, "y": 100, "w": 260, "h": 70, "style": "box", "lines": ["Camel route order-cache", "one consumer group"]},
        {"x": 700, "y": 230, "w": 260, "h": 70, "style": "accent", "lines": ["version guard", "WATCH, compare, MULTI and EXEC"]},
        {"x": 700, "y": 320, "w": 260, "h": 70, "style": "kernel", "lines": ["Redis hash order:{id}", "version and JSON, no persistence"]},
    ],
    edges=[
        {"x1": 170, "y1": 160, "x2": 170, "y2": 200},
        {"x1": 300, "y1": 235, "x2": 370, "y2": 135, "amber": True, "label": "write-ahead log", "ly": -10},
        {"x1": 500, "y1": 170, "x2": 500, "y2": 230, "amber": True},
        {"x1": 630, "y1": 265, "x2": 700, "y2": 135, "amber": True},
        {"x1": 830, "y1": 170, "x2": 830, "y2": 230, "amber": True},
        {"x1": 830, "y1": 300, "x2": 830, "y2": 320, "amber": True},
        {"x1": 700, "y1": 355, "x2": 300, "y2": 355, "dashed": True, "label": "read", "ly": -10},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Redis is written only from events of committed orders"},
    ],
)
