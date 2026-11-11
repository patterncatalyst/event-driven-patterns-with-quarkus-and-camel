"""Twelve consumer threads on nine partitions: the first nine each own one partition and three own nothing."""

from generate_diagram import emit

emit(
    "06-concurrency-cap",
    960,
    405,
    bands=[
        {"x": 10, "y": 215, "w": 940, "h": 120, "fill": "#fafafa", "label": ""},
    ],
    nodes=[
        {"x": 20, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p0", "orders 0"]},
        {"x": 124, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p1", "orders 1"]},
        {"x": 228, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p2", "orders 2"]},
        {"x": 332, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p3", "payments 0"]},
        {"x": 436, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p4", "payments 1"]},
        {"x": 540, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p5", "payments 2"]},
        {"x": 644, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p6", "shipping 0"]},
        {"x": 748, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p7", "shipping 1"]},
        {"x": 852, "y": 75, "w": 92, "h": 46, "style": "box", "lines": ["p8", "shipping 2"]},
        {"x": 20, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c1", "p0"]},
        {"x": 98, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c2", "p1"]},
        {"x": 176, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c3", "p2"]},
        {"x": 254, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c4", "p3"]},
        {"x": 332, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c5", "p4"]},
        {"x": 410, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c6", "p5"]},
        {"x": 488, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c7", "p6"]},
        {"x": 566, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c8", "p7"]},
        {"x": 644, "y": 250, "w": 64, "h": 52, "style": "accent", "lines": ["c9", "p8"]},
        {"x": 722, "y": 250, "w": 64, "h": 52, "style": "ghost", "lines": ["c10", "idle"]},
        {"x": 800, "y": 250, "w": 64, "h": 52, "style": "ghost", "lines": ["c11", "idle"]},
        {"x": 878, "y": 250, "w": 64, "h": 52, "style": "ghost", "lines": ["c12", "idle"]},
    ],
    edges=[
        {"x1": 66, "y1": 121, "x2": 52, "y2": 250, "amber": True},
        {"x1": 170, "y1": 121, "x2": 130, "y2": 250, "amber": True},
        {"x1": 274, "y1": 121, "x2": 208, "y2": 250, "amber": True},
        {"x1": 378, "y1": 121, "x2": 286, "y2": 250, "amber": True},
        {"x1": 482, "y1": 121, "x2": 364, "y2": 250, "amber": True},
        {"x1": 586, "y1": 121, "x2": 442, "y2": 250, "amber": True},
        {"x1": 690, "y1": 121, "x2": 520, "y2": 250, "amber": True},
        {"x1": 794, "y1": 121, "x2": 598, "y2": 250, "amber": True},
        {"x1": 898, "y1": 121, "x2": 676, "y2": 250, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 355, "anchor": "middle", "size": 12, "color": "#555555", "text": "One consumer group, consumersCount=12, RoundRobinAssignor: twelve members"},
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444", "text": "Parallelism stops at the partition count"},
        {"x": 480, "y": 60, "anchor": "middle", "size": 12, "color": "#555555", "text": "Three topics, three partitions each: nine partitions"},
        {"x": 480, "y": 378, "anchor": "middle", "size": 12, "color": "#555555", "text": "Each consumersCount thread is a separate group member. Members beyond the partition count own nothing. The default RangeAssignor leaves nine idle."},
    ],
)
