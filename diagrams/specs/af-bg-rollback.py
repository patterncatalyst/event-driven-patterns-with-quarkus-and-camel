"""Green is live; a rollback copies green's offsets back to blue's existing group before blue starts, instead of letting blue resume at its old position."""

from generate_diagram import emit

emit(
    "af-bg-rollback",
    1000,
    390,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 100, "fill": "#fafafa", "label": "Green live"},
        {"x": 20, "y": 180, "w": 960, "h": 150, "fill": "#fff8ef", "label": "Rollback, the same four steps the other way"},
    ],
    nodes=[
        {"x": 40, "y": 90, "w": 920, "h": 55, "style": "accent", "lines": ["notify-green, 3 replicas, group notify-green is serving", "notify-blue, 0 replicas, its group keeps its old offsets"]},
        {"x": 40, "y": 215, "w": 210, "h": 90, "style": "box", "lines": ["1 Scale green to 0", "wait until the group", "notify-green is Empty"]},
        {"x": 285, "y": 215, "w": 210, "h": 90, "style": "box", "lines": ["2 Export green's", "committed offsets"]},
        {"x": 530, "y": 215, "w": 210, "h": 90, "style": "box", "lines": ["3 Import into blue", "the group exists and holds", "older offsets: they are replaced"]},
        {"x": 775, "y": 215, "w": 185, "h": 90, "style": "accent", "lines": ["4 Scale blue to 3", "resumes after what", "green processed"]},
    ],
    edges=[
        {"x1": 250, "y1": 260, "x2": 285, "y2": 260},
        {"x1": 495, "y1": 260, "x2": 530, "y2": 260},
        {"x1": 740, "y1": 260, "x2": 775, "y2": 260, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Rollback is a cut-over in the other direction"},
        {"x": 500, "y": 360, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "Without step 3 blue resumes at its old offsets and reads again everything since the cut-over"},
    ],
)
