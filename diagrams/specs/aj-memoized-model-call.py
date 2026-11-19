"""The first arrival pays for one model call and commits the answer; a redelivery after a crash finds the row already there and calls the model zero times."""

from generate_diagram import emit

emit(
    "aj-memoized-model-call",
    960,
    460,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 150, "fill": "#fff8ef", "label": "First arrival: the guard finds no row, the model is called once"},
        {"x": 20, "y": 250, "w": 920, "h": 150, "fill": "#fafafa", "label": "Redelivery after a crash: the guard finds the row, the model is never called"},
    ],
    icons=[
        {"x": 35, "y": 104, "w": 110, "h": 90, "kind": "message", "label": "note"},
        {"x": 165, "y": 104, "w": 110, "h": 90, "kind": "channel", "label": "delivery-exceptions"},
        {"x": 605, "y": 104, "w": 110, "h": 90, "kind": "endpoint", "label": "model"},
        {"x": 35, "y": 294, "w": 110, "h": 90, "kind": "message", "label": "note, redelivered"},
        {"x": 165, "y": 294, "w": 110, "h": 90, "kind": "channel", "label": "delivery-exceptions"},
    ],
    nodes=[
        {"x": 315, "y": 100, "w": 150, "h": 80, "style": "kernel", "lines": ["MemoizedAnalysisService", "guard: no row"]},
        {"x": 755, "y": 100, "w": 165, "h": 80, "style": "accent", "lines": ["analysis table", "row committed"]},
        {"x": 315, "y": 290, "w": 150, "h": 80, "style": "kernel", "lines": ["MemoizedAnalysisService", "guard: row found"]},
        {"x": 755, "y": 290, "w": 165, "h": 80, "style": "accent", "lines": ["analysis table", "row reused"]},
        {"x": 40, "y": 425, "w": 880, "h": 24, "style": "ink", "lines": ["ANALYSIS_DEDUP=off removes the guard: a redelivery calls the model again"]},
    ],
    edges=[
        {"x1": 145, "y1": 140, "x2": 165, "y2": 140},
        {"x1": 275, "y1": 140, "x2": 315, "y2": 140},
        {"x1": 465, "y1": 140, "x2": 605, "y2": 140},
        {"x1": 715, "y1": 140, "x2": 755, "y2": 140},
        {"x1": 145, "y1": 330, "x2": 165, "y2": 330},
        {"x1": 275, "y1": 330, "x2": 315, "y2": 330},
        {"x1": 465, "y1": 330, "x2": 755, "y2": 330, "dashed": True, "label": "no model call"},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One answer per note, committed once and reused on redelivery"},
    ],
)
