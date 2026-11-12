"""A command loads the stream, rebuilds the aggregate, decides and appends in one transaction; only two event types leave through the outbox."""

from generate_diagram import emit

emit(
    "14-aggregate-command-cycle",
    960,
    520,
    bands=[
        {"x": 20, "y": 60, "w": 620, "h": 430, "fill": "#fff8ef", "label": "One transaction in order-service"},
        {"x": 670, "y": 60, "w": 270, "h": 430, "fill": "#fafafa", "label": "Out of the service"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 280, "h": 56, "style": "box", "lines": ["Command", "REST, PaymentProcessed, ShipmentScheduled"]},
        {"x": 350, "y": 100, "w": 270, "h": 56, "style": "box", "lines": ["EventStore.load(orderId)", "rows in version order"]},
        {"x": 350, "y": 190, "w": 270, "h": 56, "style": "box", "lines": ["OrderAggregate.rehydrate", "fold the events into the state"]},
        {"x": 40, "y": 190, "w": 280, "h": 56, "style": "accent", "lines": ["decide(command)", "refuse, or return the new events"]},
        {"x": 40, "y": 290, "w": 580, "h": 70, "style": "accent", "lines": ["EventStore.append at the loaded version + 1", "unique (stream_id, version): a second writer at the same version fails"]},
        {"x": 40, "y": 390, "w": 580, "h": 70, "style": "kernel", "lines": ["orders.events: every event", "orders.outboxevent: a row for OrderPlaced and OrderCancelled only"]},
        {"x": 700, "y": 290, "w": 210, "h": 70, "style": "ink", "lines": ["Debezium connector", "and Event Router"]},
        {"x": 700, "y": 390, "w": 210, "h": 70, "style": "ink", "lines": ["Kafka topics", "edp.orders.placed, edp.orders.cancelled"]},
    ],
    edges=[
        {"x1": 320, "y1": 128, "x2": 350, "y2": 128},
        {"x1": 485, "y1": 156, "x2": 485, "y2": 190},
        {"x1": 350, "y1": 218, "x2": 320, "y2": 218},
        {"x1": 180, "y1": 246, "x2": 180, "y2": 290},
        {"x1": 330, "y1": 360, "x2": 330, "y2": 390},
        {"x1": 620, "y1": 425, "x2": 700, "y2": 325, "amber": True},
        {"x1": 805, "y1": 360, "x2": 805, "y2": 390},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The stream is the state; the outbox carries only the public events"},
    ],
)
