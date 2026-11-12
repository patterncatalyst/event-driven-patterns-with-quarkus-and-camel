"""A load reads one snapshot row and the events after its version; without a snapshot it reads every event."""

from generate_diagram import emit

emit(
    "15-snapshot-plus-tail",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 150, "fill": "#fafafa", "label": "inventory.events, stream SKU-1503, ordered by version"},
        {"x": 20, "y": 240, "w": 450, "h": 190, "fill": "#fff8ef", "label": "Load with the snapshot"},
        {"x": 490, "y": 240, "w": 450, "h": 190, "fill": "#fafafa", "label": "Load without it"},
    ],
    nodes=[
        {"x": 50, "y": 100, "w": 480, "h": 70, "style": "ink", "lines": ["versions 1 to s", "already folded into the snapshot"]},
        {"x": 540, "y": 100, "w": 370, "h": 70, "style": "accent", "lines": ["versions s+1 to head", "fewer than N events when the policy keeps up"]},
        {"x": 50, "y": 280, "w": 190, "h": 66, "style": "box", "lines": ["1 snapshot row", "version s, schema 1"]},
        {"x": 260, "y": 280, "w": 190, "h": 66, "style": "accent", "lines": ["the tail", "events after s"]},
        {"x": 150, "y": 365, "w": 210, "h": 50, "style": "box", "lines": ["state at head"]},
        {"x": 520, "y": 280, "w": 390, "h": 66, "style": "box", "lines": ["every event from version 1", "100,000 and more rows to read and map"]},
        {"x": 600, "y": 365, "w": 210, "h": 50, "style": "box", "lines": ["state at head"]},
    ],
    edges=[
        {"x1": 290, "y1": 170, "x2": 145, "y2": 280, "dashed": True},
        {"x1": 725, "y1": 170, "x2": 355, "y2": 280, "amber": True},
        {"x1": 145, "y1": 346, "x2": 230, "y2": 365},
        {"x1": 355, "y1": 346, "x2": 280, "y2": 365, "amber": True},
        {"x1": 715, "y1": 346, "x2": 705, "y2": 365},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A snapshot is the fold at version s: load it, then replay only what follows"},
    ],
)
