"""During a stop and restart no pod exists: the producer keeps writing, the backlog grows and no row is written."""

from generate_diagram import emit

emit(
    "af-stop-gap",
    1000,
    320,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 210, "fill": "#fff8ef", "label": "The gap: old pods gone, new pods not ready"},
    ],
    nodes=[
        {"x": 40, "y": 125, "w": 150, "h": 70, "style": "user", "lines": ["Producer Job", "keeps sending", "at the same rate"]},
        {"x": 240, "y": 110, "w": 190, "h": 100, "style": "accent", "lines": ["edp.orders.placed", "records keep arriving", "backlog grows"]},
        {"x": 500, "y": 125, "w": 200, "h": 70, "style": "ghost", "lines": ["no pods", "the group has no member"]},
        {"x": 780, "y": 125, "w": 180, "h": 70, "style": "kernel", "lines": ["notifications.notification", "no new rows"]},
    ],
    edges=[
        {"x1": 190, "y1": 160, "x2": 240, "y2": 160, "amber": True},
        {"x1": 430, "y1": 160, "x2": 500, "y2": 160, "dashed": True},
        {"x1": 700, "y1": 160, "x2": 780, "y2": 160, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The topic keeps the records while no consumer runs"},
        {"x": 500, "y": 296, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The backlog grows by the producer's rate for as long as the new pods need to start"},
    ],
)
