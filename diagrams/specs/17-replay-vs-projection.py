"""One append-only store, two ways to answer the same question: fold every aggregate on request, or read a table the projector keeps current."""

from generate_diagram import emit

emit(
    "17-replay-vs-projection",
    960,
    520,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 110, "fill": "#fafafa", "label": "Write side: every command appends events and commits"},
        {"x": 20, "y": 200, "w": 920, "h": 130, "fill": "#fafafa", "label": "Replay: at the time of the question"},
        {"x": 20, "y": 360, "w": 920, "h": 130, "fill": "#fff8ef", "label": "Projection: before the question, and kept current"},
    ],
    nodes=[
        {"x": 50, "y": 100, "w": 230, "h": 56, "style": "box", "lines": ["OrderCommands", "decide, append at version"]},
        {"x": 410, "y": 100, "w": 300, "h": 56, "style": "ink", "lines": ["orders.events", "one row per event, append only"]},
        {"x": 50, "y": 245, "w": 230, "h": 66, "style": "box", "lines": ["ReplayReport", "every stream, one OrderAggregate at a time"]},
        {"x": 410, "y": 245, "w": 230, "h": 66, "style": "ghost", "lines": ["fold, count the shipped", "cost grows with the store"]},
        {"x": 700, "y": 245, "w": 210, "h": 66, "style": "accent", "lines": ["carrier, day, shipments", "the answer"]},
        {"x": 50, "y": 405, "w": 230, "h": 66, "style": "box", "lines": ["CarrierDailyProjector", "tick, batch, checkpoint"]},
        {"x": 410, "y": 405, "w": 230, "h": 66, "style": "ink", "lines": ["carrier_daily_shipments", "27 rows, whatever the store holds"]},
        {"x": 700, "y": 405, "w": 210, "h": 66, "style": "accent", "lines": ["carrier, day, shipments", "one table scan"]},
    ],
    edges=[
        {"x1": 280, "y1": 128, "x2": 410, "y2": 128},
        {"x1": 560, "y1": 156, "x2": 165, "y2": 245, "dashed": True},
        {"x1": 280, "y1": 278, "x2": 410, "y2": 278, "dashed": True},
        {"x1": 640, "y1": 278, "x2": 700, "y2": 278, "dashed": True},
        {"x1": 560, "y1": 156, "x2": 165, "y2": 405, "amber": True},
        {"x1": 280, "y1": 438, "x2": 410, "y2": 438, "amber": True},
        {"x1": 640, "y1": 438, "x2": 700, "y2": 438, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The same question over the same events: read them all now, or read what was kept up to date"},
        {"x": 480, "y": 510, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "A new question costs code for the replay, and code plus a rebuild from the first event for the projection."},
    ],
)
