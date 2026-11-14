"""One order on its way to stock: the four places a signal can be read, and what each place cannot see."""

from generate_diagram import emit

BACKLOG_THRESHOLD_BYTES = 16384   # the alert threshold, above the noise at rest and below twenty waiting orders

emit(
    "26-where-each-signal-lives",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 130, "fill": "#fff8ef", "label": "The flow"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 150, "h": 70, "style": "box", "lines": ["order-service", "order and outbox row", "in one transaction"]},
        {"x": 225, "y": 100, "w": 150, "h": 70, "style": "box", "lines": ["replication slot", "the row is already", "deleted from the table"]},
        {"x": 410, "y": 100, "w": 150, "h": 70, "style": "box", "lines": ["Connect", "reads the slot,", "writes the topic"]},
        {"x": 595, "y": 100, "w": 150, "h": 70, "style": "ink", "lines": ["edp.orders.placed", "one partition"]},
        {"x": 780, "y": 100, "w": 170, "h": 70, "style": "box", "lines": ["inventory-service", "reserve, or dead letter"]},
        {"x": 225, "y": 250, "w": 150, "h": 100, "style": "accent", "lines": ["Backlog", "bytes in the slot", "not yet confirmed", "outbox.backlog"]},
        {"x": 595, "y": 250, "w": 150, "h": 100, "style": "accent", "lines": ["Group lag", "read at the broker", "by the collector", "kafka_metrics"]},
        {"x": 780, "y": 250, "w": 170, "h": 100, "style": "accent", "lines": ["Event age", "now minus the time", "stamped at commit", "order.flow.event.age"]},
        {"x": 40, "y": 250, "w": 150, "h": 100, "style": "ghost", "lines": ["Outbox table", "always empty", "reads 0 in a stall"]},
    ],
    edges=[
        {"x1": 190, "y1": 135, "x2": 225, "y2": 135},
        {"x1": 375, "y1": 135, "x2": 410, "y2": 135},
        {"x1": 560, "y1": 135, "x2": 595, "y2": 135},
        {"x1": 745, "y1": 135, "x2": 780, "y2": 135},
        {"x1": 300, "y1": 170, "x2": 300, "y2": 250, "amber": True},
        {"x1": 670, "y1": 170, "x2": 670, "y2": 250, "amber": True},
        {"x1": 865, "y1": 170, "x2": 865, "y2": 250, "amber": True},
        {"x1": 115, "y1": 170, "x2": 115, "y2": 250, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Each signal is read where the work waits, not inside the service that is stuck"},
        {"x": 500, "y": 395, "anchor": "middle", "size": 13, "color": "#555555",
         "text": f"A stopped consumer reports nothing about itself; the alert fires on backlog above {BACKLOG_THRESHOLD_BYTES} bytes"},
    ],
)
