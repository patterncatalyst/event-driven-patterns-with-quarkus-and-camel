"""Before a rolling update: three pods of revision 1 each own one partition."""

from generate_diagram import emit

emit(
    "af-roll-before",
    1000,
    320,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 210, "fill": "#fafafa", "label": "Before: revision 1, three pods"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 190, "h": 100, "style": "kernel", "lines": ["edp.orders.placed", "partitions 0, 1, 2", "group notify-roll"]},
        {"x": 330, "y": 90, "w": 220, "h": 46, "style": "box", "lines": ["revision 1 pod", "owns partition 0"]},
        {"x": 330, "y": 150, "w": 220, "h": 46, "style": "box", "lines": ["revision 1 pod", "owns partition 1"]},
        {"x": 330, "y": 210, "w": 220, "h": 46, "style": "box", "lines": ["revision 1 pod", "owns partition 2"]},
        {"x": 680, "y": 110, "w": 280, "h": 100, "style": "ghost", "lines": ["Deployment strategy", "RollingUpdate", "maxSurge 1, maxUnavailable 0"]},
    ],
    edges=[
        {"x1": 230, "y1": 150, "x2": 330, "y2": 113},
        {"x1": 230, "y1": 160, "x2": 330, "y2": 173},
        {"x1": 230, "y1": 170, "x2": 330, "y2": 233},
        {"x1": 680, "y1": 160, "x2": 550, "y2": 173, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A rolling update starts from three serving members"},
        {"x": 500, "y": 296, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The producer keeps sending at a fixed rate throughout"},
    ],
)
