"""Two ways to read the output topic: the first match stops at the first record, the drain reads the whole window."""

from generate_diagram import emit

emit(
    "aa-drain-vs-first-match",
    1000,
    420,
    bands=[
        {"x": 20, "y": 70, "w": 960, "h": 100, "fill": "#fff8ef", "label": "edp.orders.notified, one repeated event"},
    ],
    nodes=[
        {"x": 60, "y": 105, "w": 190, "h": 50, "style": "box", "lines": ["record 1 for O-3602"]},
        {"x": 330, "y": 105, "w": 190, "h": 50, "style": "accent", "lines": ["record 2 for O-3602"]},
        {"x": 640, "y": 105, "w": 300, "h": 50, "style": "ghost", "lines": ["end of the wait window"]},
        {"x": 60, "y": 235, "w": 400, "h": 70, "style": "ink", "lines": ["First match", "stops at record 1 and passes"]},
        {"x": 540, "y": 235, "w": 400, "h": 70, "style": "kernel", "lines": ["Drain for the whole window", "counts 2, expects 1, fails"]},
    ],
    edges=[
        {"x1": 155, "y1": 155, "x2": 155, "y2": 235, "amber": True},
        {"x1": 425, "y1": 155, "x2": 640, "y2": 235, "amber": True},
        {"x1": 790, "y1": 155, "x2": 790, "y2": 235, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Only a read that waits can see a second record"},
        {"x": 500, "y": 370, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "The window has to outlast the route, and a longer window costs every run that long"},
    ],
)
