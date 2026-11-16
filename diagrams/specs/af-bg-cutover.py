"""Cut-over: scale blue to zero, export its committed offsets, import them into green's group, scale green up; records after blue's last commit are processed by both groups."""

from generate_diagram import emit

emit(
    "af-bg-cutover",
    1000,
    330,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 150, "fill": "#fff8ef", "label": "Cut-over, in this order"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 210, "h": 90, "style": "box", "lines": ["1 Scale blue to 0", "wait until the group", "notify-blue is Empty"]},
        {"x": 285, "y": 100, "w": 210, "h": 90, "style": "box", "lines": ["2 Export offsets", "--to-current --dry-run", "--export: topic,partition,offset"]},
        {"x": 530, "y": 100, "w": 210, "h": 90, "style": "box", "lines": ["3 Import into green", "--from-file --execute", "on group notify-green"]},
        {"x": 775, "y": 100, "w": 185, "h": 90, "style": "accent", "lines": ["4 Scale green to 3", "reads from blue's", "committed offsets"]},
        {"x": 40, "y": 245, "w": 920, "h": 60, "style": "ink", "lines": ["Records blue processed after its last commit are processed again by green", "the overlap is bounded by the commit interval, not zero; nothing is missing"]},
    ],
    edges=[
        {"x1": 250, "y1": 145, "x2": 285, "y2": 145},
        {"x1": 495, "y1": 145, "x2": 530, "y2": 145},
        {"x1": 740, "y1": 145, "x2": 775, "y2": 145, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Green starts where blue's commits ended"},
    ],
)
