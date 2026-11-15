"""One order as a waterfall of spans across three services, with the hop through the log and Connect marked."""

from generate_diagram import emit

LEFT = 190   # x of the first bar
ROW = 46     # distance between rows
TOP = 70     # y of the first row

rows = [
    # (label, service, x offset, width, style)
    ("POST /orders", "order-service", 0, 90, "user"),
    ("outbox-write", "order-service", 40, 40, "user"),
    ("db-log-write", "connect", 50, 330, "accent"),
    ("debezium-read", "connect", 140, 120, "accent"),
    ("edp.orders.placed receive", "inventory-service", 380, 50, "box"),
    ("reserve stock", "inventory-service", 385, 40, "box"),
]

nodes = []
for i, (label, service, dx, width, style) in enumerate(rows):
    nodes.append({"x": LEFT + dx, "y": TOP + i * ROW, "w": width, "h": 30, "style": style, "lines": [""]})

# The hop through the log and Connect: from the end of outbox-write to the start of the consumer span.
nodes.append({"x": LEFT + 80, "y": TOP + 6 * ROW - 6, "w": 300, "h": 34, "style": "ghost",
              "lines": ["CDC hop: the log, then the relay"]})

notes = [
    {"x": 410, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
     "text": "The consumer's span is a child of db-log-write: the gap is time outside the services"},
]
for i, (label, service, dx, width, style) in enumerate(rows):
    notes.append({"x": 20, "y": TOP + i * ROW + 14, "anchor": "start", "size": 12.5, "bold": True, "color": "#111111", "text": label})
    notes.append({"x": 20, "y": TOP + i * ROW + 28, "anchor": "start", "size": 11, "color": "#777777", "text": service})

emit(
    "25-trace-waterfall",
    820,
    TOP + 7 * ROW + 40,
    nodes=nodes,
    edges=[],
    notes=notes,
)
