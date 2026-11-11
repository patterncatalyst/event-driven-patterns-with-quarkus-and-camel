"""The outbox row commits with the order; Debezium reads it from the write-ahead log and routes it to the topic."""

from generate_diagram import emit

emit(
    "08-outbox-wal-router",
    960,
    460,
    bands=[
        {"x": 20, "y": 60, "w": 450, "h": 360, "fill": "#fff8ef", "label": "One transaction in order-service"},
        {"x": 500, "y": 60, "w": 440, "h": 360, "fill": "#fafafa", "label": "Kafka Connect"},
    ],
    nodes=[
        {"x": 50, "y": 100, "w": 390, "h": 60, "style": "box", "lines": ["OrderService.place()", "persist the order, fire OrderPlacedEvent"]},
        {"x": 50, "y": 190, "w": 390, "h": 60, "style": "accent", "lines": ["orders.orders and orders.outboxevent", "both rows commit together or not at all"]},
        {"x": 50, "y": 290, "w": 390, "h": 70, "style": "kernel", "lines": ["PostgreSQL write-ahead log", "replication slot edp_order_outbox"]},
        {"x": 530, "y": 100, "w": 380, "h": 60, "style": "box", "lines": ["Debezium PostgreSQL connector", "reads inserts of orders.outboxevent only"]},
        {"x": 530, "y": 190, "w": 380, "h": 70, "style": "box", "lines": ["EventRouter transform", "topic column picks the topic, aggregateid the key"]},
        {"x": 530, "y": 290, "w": 380, "h": 70, "style": "ink", "lines": ["edp.orders.placed", "key = order id; headers id and eventType"]},
    ],
    edges=[
        {"x1": 245, "y1": 160, "x2": 245, "y2": 190},
        {"x1": 245, "y1": 250, "x2": 245, "y2": 290},
        {"x1": 440, "y1": 325, "x2": 530, "y2": 135, "amber": True},
        {"x1": 720, "y1": 160, "x2": 720, "y2": 190},
        {"x1": 720, "y1": 260, "x2": 720, "y2": 290},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Nothing publishes from application memory"},
    ],
)
