"""Blue is live under its own consumer group; green is the same image under another group, at zero replicas, with no offsets."""

from generate_diagram import emit

emit(
    "af-bg-blue-live",
    1000,
    340,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 230, "fill": "#fafafa", "label": "Blue live"},
    ],
    nodes=[
        {"x": 40, "y": 115, "w": 190, "h": 100, "style": "kernel", "lines": ["edp.orders.placed", "partitions 0, 1, 2", "the producer keeps sending"]},
        {"x": 330, "y": 95, "w": 250, "h": 70, "style": "accent", "lines": ["notify-blue, 3 replicas", "group notify-blue", "serving"]},
        {"x": 330, "y": 190, "w": 250, "h": 70, "style": "ghost", "lines": ["notify-green, 0 replicas", "group notify-green", "no member, no offsets"]},
        {"x": 700, "y": 95, "w": 260, "h": 70, "style": "ink", "lines": ["committed offsets of", "notify-blue"]},
        {"x": 700, "y": 190, "w": 260, "h": 70, "style": "ghost", "lines": ["committed offsets of", "notify-green: none"]},
    ],
    edges=[
        {"x1": 230, "y1": 150, "x2": 330, "y2": 130},
        {"x1": 230, "y1": 180, "x2": 330, "y2": 225, "dashed": True},
        {"x1": 580, "y1": 130, "x2": 700, "y2": 130},
        {"x1": 580, "y1": 225, "x2": 700, "y2": 225, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One consumer group per colour"},
        {"x": 500, "y": 315, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "A new group with no offsets would replay the topic or skip what blue has not read"},
    ],
)
