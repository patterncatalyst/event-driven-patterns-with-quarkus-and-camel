"""The analysis store is append-only and versioned; a prompt bump re-analyses only the stale rows, and a disposition snapshot lets replay re-derive decisions with no model call."""

from generate_diagram import emit

emit(
    "aj-backfill-and-snapshot",
    960,
    460,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 150, "fill": "#fff8ef", "label": "A pin or prompt bump: only the stale rows are re-analysed"},
        {"x": 20, "y": 250, "w": 920, "h": 150, "fill": "#fafafa", "label": "Replay: dispositions are re-derived from the snapshot, no model call"},
    ],
    nodes=[
        {"x": 35, "y": 100, "w": 230, "h": 80, "style": "accent",
         "lines": ["analysis store, append-only", "v1 rows: current", "v2 rows: stale"]},
        {"x": 790, "y": 100, "w": 130, "h": 80, "style": "accent", "lines": ["analysis store", "v2 row appended"]},
        {"x": 35, "y": 290, "w": 230, "h": 80, "style": "accent", "lines": ["DispositionSnapshot", "one row per note"]},
        {"x": 790, "y": 290, "w": 130, "h": 80, "style": "box", "lines": ["decisions", "re-derived"]},
        {"x": 40, "y": 425, "w": 880, "h": 24, "style": "ink", "lines": ["REPLAY_MODE=reanalyse bypasses the snapshot and calls the model again"]},
    ],
    icons=[
        {"x": 300, "y": 104, "w": 150, "h": 90, "kind": "translator", "label": "re-analyse stale rows"},
        {"x": 590, "y": 104, "w": 150, "h": 90, "kind": "endpoint", "label": "model"},
        {"x": 300, "y": 294, "w": 150, "h": 90, "kind": "channel", "label": "/demo/replay"},
        {"x": 590, "y": 294, "w": 150, "h": 90, "kind": "router", "label": "DecisionService"},
    ],
    edges=[
        {"x1": 265, "y1": 140, "x2": 300, "y2": 140, "label": "stale only"},
        {"x1": 450, "y1": 140, "x2": 590, "y2": 140},
        {"x1": 740, "y1": 140, "x2": 790, "y2": 140, "amber": True},
        {"x1": 265, "y1": 330, "x2": 300, "y2": 330},
        {"x1": 450, "y1": 330, "x2": 590, "y2": 330},
        {"x1": 740, "y1": 330, "x2": 790, "y2": 330, "dashed": True, "label": "no model call"},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Backfill touches what changed; replay never calls the model"},
    ],
)
