"""The same ten arrivals grouped by slot of event time (Kafka Streams) and by size five or quiet time at arrival (Camel Aggregator)."""

from generate_diagram import emit

emit(
    "21-window-vs-batch",
    960,
    440,
    bands=[
        {"x": 20, "y": 90, "w": 920, "h": 150, "fill": "#fff8ef", "label": "Kafka Streams: event-time slots, one final list per carrier per slot"},
        {"x": 20, "y": 260, "w": 920, "h": 150, "fill": "#fafafa", "label": "Camel Aggregator: arrival order, size 5 or 10 s of quiet"},
    ],
    nodes=[
        {"x": 20, "y": 30, "w": 920, "h": 44, "style": "ink", "lines": ["Arrivals for FASTLINE: F1 to F7 (09:01 to 09:07), F8 (09:16), F9 (09:10, late arrival), L1 (09:05, after the slot closed)"]},
        {"x": 50, "y": 130, "w": 400, "h": 80, "style": "accent", "lines": ["Slot 09:00 list", "F1 to F7 and F9, count 8", "closed by the window"]},
        {"x": 480, "y": 130, "w": 220, "h": 80, "style": "ghost", "lines": ["Slot 09:15 list", "F8, still open"]},
        {"x": 730, "y": 130, "w": 180, "h": 80, "style": "kernel", "lines": ["L1", "dropped and counted"]},
        {"x": 50, "y": 300, "w": 210, "h": 80, "style": "box", "lines": ["Batch 1", "F1 to F5", "closed by size"]},
        {"x": 280, "y": 300, "w": 190, "h": 80, "style": "box", "lines": ["Batch 2", "F6, F7", "closed by timeout"]},
        {"x": 490, "y": 300, "w": 190, "h": 80, "style": "box", "lines": ["Batch 3", "F8, F9", "closed by timeout"]},
        {"x": 700, "y": 300, "w": 210, "h": 80, "style": "accent", "lines": ["Batch 4", "L1 alone", "closed by timeout"]},
    ],
    edges=[],
    notes=[
        {"x": 480, "y": 425, "anchor": "middle", "bold": False, "size": 12, "color": "#555555",
         "text": "Same input, same output type, different rule for what belongs together"},
    ],
)
