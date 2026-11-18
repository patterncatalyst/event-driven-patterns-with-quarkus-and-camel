"""Two runs against a quota of 1750m: the second run's reconcile Job waits suspended; without the queue both start and ask for 3000m."""

from generate_diagram import emit

emit(
    "ai-queueing-timeline",
    1000,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 200, "fill": "#fff8ef", "label": "With the queue: quota 1750m, one reconcile Job at 1500m at a time"},
        {"x": 20, "y": 280, "w": 960, "h": 150, "fill": "#fafafa", "label": "Without the queue label: nothing limits the demand"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 120, "h": 50, "style": "box", "lines": ["run A reconcile"]},
        {"x": 180, "y": 110, "w": 330, "h": 50, "style": "accent", "lines": ["running, 3 pods, 1500m"]},
        {"x": 40, "y": 190, "w": 120, "h": 50, "style": "box", "lines": ["run B reconcile"]},
        {"x": 180, "y": 190, "w": 330, "h": 50, "style": "ghost", "lines": ["suspended, WaitingForQuota, 0 pods"]},
        {"x": 530, "y": 190, "w": 330, "h": 50, "style": "accent", "lines": ["admitted, running, 3 pods, 1500m"]},
        {"x": 40, "y": 330, "w": 120, "h": 40, "style": "box", "lines": ["run A reconcile"]},
        {"x": 180, "y": 330, "w": 330, "h": 40, "style": "user", "lines": ["running, 1500m"]},
        {"x": 40, "y": 380, "w": 120, "h": 40, "style": "box", "lines": ["run B reconcile"]},
        {"x": 180, "y": 380, "w": 330, "h": 40, "style": "user", "lines": ["running at once, 1500m"]},
        {"x": 540, "y": 345, "w": 420, "h": 60, "style": "ink", "lines": ["3000m requested, quota 1750m", "nothing holds the second run back"]},
    ],
    edges=[
        {"x1": 510, "y1": 135, "x2": 600, "y2": 190, "amber": True, "label": "quota released", "lx": 20, "ly": -8},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The quota, not the node, decides what runs"},
    ],
)
