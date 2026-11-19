"""The same ten arrivals grouped by slot of event time (Kafka Streams Aggregator) and by size five or quiet time at arrival (Camel Aggregator)."""

from generate_diagram import emit

emit(
    "21-window-vs-batch",
    980,
    540,
    bands=[
        {"x": 20, "y": 100, "w": 940, "h": 190, "fill": "#fff8ef", "label": "Kafka Streams: event-time slots, one final list per carrier per slot"},
        {"x": 20, "y": 310, "w": 940, "h": 190, "fill": "#fafafa", "label": "Camel Aggregator: arrival order, size 5 or 10 s of quiet"},
    ],
    nodes=[
        {"x": 20, "y": 30, "w": 940, "h": 48, "style": "ink", "lines": ["Arrivals for FASTLINE: F1 to F7 (09:01 to 09:07), F8 (09:16), F9 (09:10, late arrival), L1 (09:05, after the slot closed)"]},
        {"x": 400, "y": 140, "w": 220, "h": 100, "style": "ghost", "lines": ["Slot 09:15 list", "F8, still open"]},
        {"x": 700, "y": 140, "w": 180, "h": 100, "style": "kernel", "lines": ["L1", "dropped and counted"]},
    ],
    icons=[
        {"x": 100, "y": 140, "w": 220, "h": 100, "kind": "aggregator", "label": "Slot 09:00: F1-F7, F9 (count 8), closed by window"},
        {"x": 40, "y": 350, "w": 210, "h": 100, "kind": "aggregator", "label": "Batch 1: F1 to F5, closed by size"},
        {"x": 270, "y": 350, "w": 210, "h": 100, "kind": "aggregator", "label": "Batch 2: F6, F7, closed by timeout"},
        {"x": 500, "y": 350, "w": 210, "h": 100, "kind": "aggregator", "label": "Batch 3: F8, F9, closed by timeout"},
        {"x": 730, "y": 350, "w": 210, "h": 100, "kind": "aggregator", "label": "Batch 4: L1 alone, closed by timeout"},
    ],
    edges=[],
    notes=[
        {"x": 490, "y": 525, "anchor": "middle", "bold": False, "size": 12, "color": "#555555",
         "text": "Same input, same output type, different rule for what belongs together"},
    ],
)
