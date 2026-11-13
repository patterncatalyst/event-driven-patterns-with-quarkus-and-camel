"""One append-only store, two ways to answer the same question: fold every aggregate on request, or read a table the projector keeps current."""

from generate_diagram import emit

emit(
    "17-replay-vs-projection",
    960,
    580,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 110, "fill": "#fafafa", "label": "Write side: every command appends events and commits"},
        {"x": 20, "y": 210, "w": 450, "h": 320, "fill": "#fafafa", "label": "Replay: on request"},
        {"x": 490, "y": 210, "w": 450, "h": 320, "fill": "#fff8ef", "label": "Projection: kept current"},
    ],
    nodes=[
        {"x": 50, "y": 100, "w": 230, "h": 56, "style": "box", "lines": ["OrderCommands", "decide, append at version"]},
        {"x": 410, "y": 100, "w": 300, "h": 56, "style": "ink", "lines": ["orders.events", "one row per event, append only"]},
        {"x": 50, "y": 265, "w": 390, "h": 66, "style": "box", "lines": ["ReplayReport", "every stream, one OrderAggregate at a time"]},
        {"x": 50, "y": 365, "w": 390, "h": 66, "style": "ghost", "lines": ["fold, count the shipped", "cost grows with the store"]},
        {"x": 50, "y": 455, "w": 390, "h": 56, "style": "accent", "lines": ["carrier, day, shipments", "the answer"]},
        {"x": 520, "y": 265, "w": 390, "h": 66, "style": "box", "lines": ["CarrierDailyProjector", "tick, batch, checkpoint"]},
        {"x": 520, "y": 365, "w": 390, "h": 66, "style": "ink", "lines": ["carrier_daily_shipments", "27 rows, whatever the store holds"]},
        {"x": 520, "y": 455, "w": 390, "h": 56, "style": "accent", "lines": ["carrier, day, shipments", "one table scan"]},
    ],
    edges=[
        {"x1": 280, "y1": 128, "x2": 410, "y2": 128},
        {"x1": 440, "y1": 156, "x2": 330, "y2": 265, "dashed": True},
        {"x1": 245, "y1": 331, "x2": 245, "y2": 365, "dashed": True},
        {"x1": 245, "y1": 431, "x2": 245, "y2": 455, "dashed": True},
        {"x1": 680, "y1": 156, "x2": 800, "y2": 265, "amber": True},
        {"x1": 715, "y1": 331, "x2": 715, "y2": 365, "amber": True},
        {"x1": 715, "y1": 431, "x2": 715, "y2": 455, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The same question over the same events: read them all now, or read what was kept up to date"},
        {"x": 480, "y": 560, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "A new question costs code for the replay, and code plus a rebuild from the first event for the projection."},
    ],
)
