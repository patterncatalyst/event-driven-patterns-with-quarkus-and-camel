"""Both input topics have three partitions keyed by order id, so partition i of each holds the same orders and index i reads both."""

from generate_diagram import emit

emit(
    "ai-co-partitioned-fan-out",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 360, "h": 270, "fill": "#fafafa", "label": "Same key, same partition"},
        {"x": 420, "y": 60, "w": 560, "h": 270, "fill": "#fff8ef", "label": "reconcile, one Indexed Job"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 320, "h": 60, "style": "box", "lines": ["partition 0", "scheduled and delivered"]},
        {"x": 40, "y": 190, "w": 320, "h": 60, "style": "box", "lines": ["partition 1", "scheduled and delivered"]},
        {"x": 40, "y": 260, "w": 320, "h": 50, "style": "box", "lines": ["partition 2, scheduled and delivered"]},
        {"x": 450, "y": 100, "w": 220, "h": 60, "style": "accent", "lines": ["index 0", "reads offsets of partition 0"]},
        {"x": 450, "y": 190, "w": 220, "h": 60, "style": "accent", "lines": ["index 1", "reads offsets of partition 1"]},
        {"x": 450, "y": 260, "w": 220, "h": 50, "style": "accent", "lines": ["index 2, partition 2"]},
        {"x": 740, "y": 100, "w": 220, "h": 60, "style": "ink", "lines": ["run_partition row 0", "one row per partition"]},
        {"x": 740, "y": 190, "w": 220, "h": 60, "style": "ink", "lines": ["run_partition row 1", "DONE with its counts"]},
        {"x": 740, "y": 260, "w": 220, "h": 50, "style": "ink", "lines": ["run_partition row 2"]},
        {"x": 40, "y": 355, "w": 920, "h": 50, "style": "sub", "lines": ["An order's scheduled and delivered events share a key, so no index needs another index's partition"]},
    ],
    edges=[
        {"x1": 360, "y1": 130, "x2": 450, "y2": 130, "amber": True},
        {"x1": 360, "y1": 220, "x2": 450, "y2": 220, "amber": True},
        {"x1": 360, "y1": 285, "x2": 450, "y2": 285, "amber": True},
        {"x1": 670, "y1": 130, "x2": 740, "y2": 130},
        {"x1": 670, "y1": 220, "x2": 740, "y2": 220},
        {"x1": 670, "y1": 285, "x2": 740, "y2": 285},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The partition is the unit of work"},
    ],
)
