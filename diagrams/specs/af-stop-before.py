"""Before a stop and restart: three pods of one group each read one partition of edp.orders.placed and write one row per record."""

from generate_diagram import emit

emit(
    "af-stop-before",
    1000,
    320,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 210, "fill": "#fafafa", "label": "Before: three pods, one partition each"},
    ],
    nodes=[
        {"x": 40, "y": 125, "w": 150, "h": 70, "style": "user", "lines": ["Producer Job", "O-41000 and up", "fixed rate"]},
        {"x": 240, "y": 110, "w": 190, "h": 100, "style": "kernel", "lines": ["edp.orders.placed", "partitions 0, 1, 2", "lag near zero"]},
        {"x": 500, "y": 90, "w": 200, "h": 46, "style": "box", "lines": ["notify-stop pod", "owns partition 0"]},
        {"x": 500, "y": 150, "w": 200, "h": 46, "style": "box", "lines": ["notify-stop pod", "owns partition 1"]},
        {"x": 500, "y": 210, "w": 200, "h": 46, "style": "box", "lines": ["notify-stop pod", "owns partition 2"]},
        {"x": 780, "y": 125, "w": 180, "h": 70, "style": "ink", "lines": ["notifications.notification", "one row per record"]},
    ],
    edges=[
        {"x1": 190, "y1": 160, "x2": 240, "y2": 160},
        {"x1": 430, "y1": 150, "x2": 500, "y2": 113},
        {"x1": 430, "y1": 160, "x2": 500, "y2": 173},
        {"x1": 430, "y1": 170, "x2": 500, "y2": 233},
        {"x1": 700, "y1": 113, "x2": 780, "y2": 150},
        {"x1": 700, "y1": 173, "x2": 780, "y2": 160},
        {"x1": 700, "y1": 233, "x2": 780, "y2": 170},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A stop and restart starts from a group that is keeping up"},
        {"x": 500, "y": 296, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "Strategy Recreate: every pod is stopped before any new pod is created"},
    ],
)
