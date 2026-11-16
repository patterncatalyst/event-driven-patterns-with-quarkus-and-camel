"""After a rolling update: three pods of revision 2, every record has a row and some may have been delivered twice."""

from generate_diagram import emit

emit(
    "af-roll-after",
    1000,
    320,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 210, "fill": "#fafafa", "label": "After: revision 2, three pods"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 190, "h": 100, "style": "kernel", "lines": ["edp.orders.placed", "group notify-roll", "lag drains"]},
        {"x": 330, "y": 90, "w": 220, "h": 46, "style": "user", "lines": ["revision 2 pod", "owns a partition"]},
        {"x": 330, "y": 150, "w": 220, "h": 46, "style": "user", "lines": ["revision 2 pod", "owns a partition"]},
        {"x": 330, "y": 210, "w": 220, "h": 46, "style": "user", "lines": ["revision 2 pod", "owns a partition"]},
        {"x": 680, "y": 110, "w": 280, "h": 100, "style": "accent", "lines": ["notifications.notification", "every record has a row", "a record read again has attempts 2"]},
    ],
    edges=[
        {"x1": 230, "y1": 150, "x2": 330, "y2": 113},
        {"x1": 230, "y1": 160, "x2": 330, "y2": 173},
        {"x1": 230, "y1": 170, "x2": 330, "y2": 233},
        {"x1": 550, "y1": 113, "x2": 680, "y2": 145, "amber": True},
        {"x1": 550, "y1": 173, "x2": 680, "y2": 160, "amber": True},
        {"x1": 550, "y1": 233, "x2": 680, "y2": 175, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "At least once: nothing missing, a bounded number, possibly none, repeated"},
        {"x": 500, "y": 296, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The repeat is bounded by what was processed since the last commit at each move"},
    ],
)
