"""The same capture run by Kafka Connect and run inside the service by the Camel Debezium component."""

from generate_diagram import emit

emit(
    "09-connect-vs-embedded",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 170, "fill": "#fafafa", "label": "Kafka Connect"},
        {"x": 20, "y": 250, "w": 920, "h": 170, "fill": "#fafafa", "label": "Embedded in the service"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 150, "h": 90, "style": "kernel", "lines": ["PostgreSQL", "slot edp_inventory"]},
        {"x": 230, "y": 110, "w": 190, "h": 90, "style": "user", "lines": ["Connect worker", "connector, offsets in", "a Kafka topic"]},
        {"x": 460, "y": 110, "w": 190, "h": 90, "style": "box", "lines": ["item-changes", "envelopes, replayable"]},
        {"x": 690, "y": 110, "w": 230, "h": 90, "style": "accent", "lines": ["inventory-service", "Camel route reads the topic"]},
        {"x": 40, "y": 300, "w": 150, "h": 90, "style": "kernel", "lines": ["PostgreSQL", "slot edp_inventory_embedded"]},
        {"x": 230, "y": 300, "w": 420, "h": 90, "style": "accent", "lines": ["inventory-service, one process", "debezium-postgres route, offsets in a file", "same mapper, no envelope on Kafka"]},
        {"x": 690, "y": 300, "w": 230, "h": 90, "style": "box", "lines": ["edp.inventory.items", "same documents"]},
    ],
    edges=[
        {"x1": 190, "y1": 155, "x2": 230, "y2": 155},
        {"x1": 420, "y1": 155, "x2": 460, "y2": 155, "amber": True},
        {"x1": 650, "y1": 155, "x2": 690, "y2": 155, "amber": True},
        {"x1": 190, "y1": 345, "x2": 230, "y2": 345},
        {"x1": 650, "y1": 345, "x2": 690, "y2": 345, "amber": True},
        {"x1": 805, "y1": 200, "x2": 805, "y2": 300, "dashed": True, "label": "publishes", "lx": 8, "ly": 4},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Two readers, two slots: each one holds its own place in the log"},
        {"x": 480, "y": 450, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Embedded keeps one process and one fewer hop. Connect keeps the envelopes, the REST API and the offsets in Kafka."},
    ],
)
