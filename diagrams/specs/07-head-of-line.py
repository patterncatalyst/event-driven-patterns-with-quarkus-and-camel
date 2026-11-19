"""Consumer group: one slow job holds back the later jobs of its partition. Share group: another worker takes them."""

from generate_diagram import emit

emit(
    "07-head-of-line",
    960,
    520,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 190, "fill": "#fafafa", "label": "Consumer group: a partition has one owner"},
        {"x": 20, "y": 280, "w": 920, "h": 190, "fill": "#fff8ef", "label": "Share group: any worker takes any record"},
    ],
    nodes=[
        {"x": 300, "y": 120, "w": 270, "h": 80, "style": "ink",
         "lines": ["offsets 1, 2 ... wait", "and finish after the slow job"]},
        {"x": 630, "y": 130, "w": 270, "h": 60, "style": "ghost",
         "lines": ["worker-2, worker-3, worker-4:", "idle for this partition"]},
        {"x": 540, "y": 350, "w": 380, "h": 90, "style": "ink",
         "lines": ["later jobs finish before the slow job does,", "no ordering guarantee"]},
    ],
    icons=[
        {"x": 40, "y": 95, "w": 200, "h": 110, "kind": "endpoint",
         "label": "partition 1: offset 0 is the slow job (C-SLOW)"},
        {"x": 40, "y": 315, "w": 190, "h": 110, "kind": "channel",
         "label": "share group queue: offsets 0, 1, 2 ..."},
        {"x": 300, "y": 300, "w": 180, "h": 100, "kind": "endpoint",
         "label": "worker-1 takes one record"},
        {"x": 300, "y": 410, "w": 180, "h": 100, "kind": "endpoint",
         "label": "another worker takes the next record"},
    ],
    edges=[
        {"x1": 240, "y1": 150, "x2": 300, "y2": 150},
        {"x1": 230, "y1": 370, "x2": 300, "y2": 350, "amber": True},
        {"x1": 230, "y1": 370, "x2": 300, "y2": 460, "amber": True},
        {"x1": 480, "y1": 350, "x2": 540, "y2": 395, "amber": True},
        {"x1": 480, "y1": 460, "x2": 540, "y2": 420, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One slow job: who waits behind it"},
    ],
)
