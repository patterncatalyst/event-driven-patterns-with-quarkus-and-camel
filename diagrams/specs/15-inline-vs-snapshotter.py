"""Inline, the command that crosses N writes the snapshot and the tail stays below N; out of band, the tail grows during a burst."""

from generate_diagram import emit

emit(
    "15-inline-vs-snapshotter",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 180, "fill": "#fff8ef", "label": "Inline: the command's own transaction"},
        {"x": 20, "y": 270, "w": 920, "h": 180, "fill": "#fafafa", "label": "Out of band: a Camel route on a timer"},
    ],
    nodes=[
        {"x": 50, "y": 105, "w": 190, "h": 66, "style": "box", "lines": ["command appends", "event s+N"]},
        {"x": 290, "y": 105, "w": 200, "h": 66, "style": "accent", "lines": ["same transaction", "guarded upsert of the snapshot"]},
        {"x": 540, "y": 105, "w": 190, "h": 66, "style": "box", "lines": ["commit", "event and snapshot together"]},
        {"x": 770, "y": 105, "w": 140, "h": 66, "style": "ink", "lines": ["tail < N", "always"]},
        {"x": 50, "y": 315, "w": 190, "h": 66, "style": "box", "lines": ["burst of commands", "append, no snapshot"]},
        {"x": 290, "y": 315, "w": 200, "h": 66, "style": "ghost", "lines": ["timer tick", "sql: streams N or more past"]},
        {"x": 540, "y": 315, "w": 190, "h": 66, "style": "accent", "lines": ["split, then take(sku)", "own transaction, same guard"]},
        {"x": 770, "y": 315, "w": 140, "h": 66, "style": "ink", "lines": ["tail can pass N", "until the next tick"]},
    ],
    edges=[
        {"x1": 240, "y1": 138, "x2": 290, "y2": 138},
        {"x1": 490, "y1": 138, "x2": 540, "y2": 138, "amber": True},
        {"x1": 730, "y1": 138, "x2": 770, "y2": 138},
        {"x1": 240, "y1": 348, "x2": 290, "y2": 348, "dashed": True},
        {"x1": 490, "y1": 348, "x2": 540, "y2": 348, "amber": True},
        {"x1": 730, "y1": 348, "x2": 770, "y2": 348},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Where the snapshot is written decides how long the tail can get"},
        {"x": 480, "y": 480, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Both write the same row, so a load cannot tell which one wrote it."},
    ],
)
