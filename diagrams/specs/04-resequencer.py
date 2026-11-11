"""Replicas forward their events to a one-partition funnel; the resequencer restores stream order."""

from generate_diagram import emit

emit(
    "04-resequencer",
    960,
    470,
    nodes=[
        {"x": 40, "y": 60, "w": 240, "h": 70, "style": "box", "lines": ["order replica B", "forwards 2 InTransit, 3 Delivered"]},
        {"x": 40, "y": 250, "w": 240, "h": 70, "style": "ghost", "lines": ["order replica A", "forwards 1 Scheduled after it resumes"]},
        {"x": 350, "y": 150, "w": 220, "h": 90, "style": "box", "lines": ["funnel topic", "one partition", "arrival order: 2, 3, 1"]},
        {"x": 640, "y": 150, "w": 280, "h": 90, "style": "accent", "lines": ["resequencer", "stream mode on streamSequence", "holds 2 and 3 until 1 arrives"]},
        {"x": 640, "y": 320, "w": 280, "h": 70, "style": "ink", "lines": ["apply", "1 Scheduled, 2 InTransit, 3 Delivered", "status ends DELIVERED"]},
    ],
    edges=[
        {"x1": 280, "y1": 95, "x2": 350, "y2": 175, "amber": True},
        {"x1": 280, "y1": 285, "x2": 350, "y2": 215, "dashed": True},
        {"x1": 570, "y1": 195, "x2": 640, "y2": 195, "amber": True},
        {"x1": 780, "y1": 240, "x2": 780, "y2": 320, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A resequencer needs every event of the sequence in one place"},
        {"x": 480, "y": 430, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "If 1 never arrives, the resequencer waits for its timeout and then moves past the gap."},
    ],
)
