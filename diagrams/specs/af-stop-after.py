"""After a stop and restart the new pods read from the committed offset: the backlog drains, nothing is missing and records after the last commit are read again."""

from generate_diagram import emit

emit(
    "af-stop-after",
    1000,
    320,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 210, "fill": "#fafafa", "label": "After: new pods rejoin the group and drain the backlog"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 190, "h": 100, "style": "kernel", "lines": ["edp.orders.placed", "committed offset", "trails the last record read"]},
        {"x": 330, "y": 90, "w": 200, "h": 46, "style": "user", "lines": ["new pod", "resumes partition 0"]},
        {"x": 330, "y": 150, "w": 200, "h": 46, "style": "user", "lines": ["new pod", "resumes partition 1"]},
        {"x": 330, "y": 210, "w": 200, "h": 46, "style": "user", "lines": ["new pod", "resumes partition 2"]},
        {"x": 640, "y": 110, "w": 320, "h": 100, "style": "accent", "lines": ["notifications.notification", "every record has a row", "attempts is 2 for records after the last commit"]},
    ],
    edges=[
        {"x1": 230, "y1": 150, "x2": 330, "y2": 113},
        {"x1": 230, "y1": 160, "x2": 330, "y2": 173},
        {"x1": 230, "y1": 170, "x2": 330, "y2": 233},
        {"x1": 530, "y1": 113, "x2": 640, "y2": 145, "amber": True},
        {"x1": 530, "y1": 173, "x2": 640, "y2": 160, "amber": True},
        {"x1": 530, "y1": 233, "x2": 640, "y2": 175, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Nothing is lost; some records are processed twice"},
        {"x": 500, "y": 296, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The client commits on a timer, so the last processed records were not yet committed"},
    ],
)
