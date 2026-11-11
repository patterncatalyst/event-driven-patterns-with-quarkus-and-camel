"""Consumer group: one slow job holds back the later jobs of its partition. Share group: other workers take them."""

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
        {"x": 40, "y": 100, "w": 250, "h": 70, "style": "accent", "lines": ["partition 1: offsets 0, 1, 2 ...", "offset 0 is the slow job (C-SLOW)"]},
        {"x": 355, "y": 100, "w": 250, "h": 70, "style": "box", "lines": ["worker-1 owns partition 1", "delivers one record after another"]},
        {"x": 670, "y": 100, "w": 250, "h": 70, "style": "ink", "lines": ["offsets 1, 2 ... wait", "and finish after the slow job"]},
        {"x": 40, "y": 190, "w": 250, "h": 50, "style": "ghost", "lines": ["worker-2, worker-3: the others", "worker-4: owns no partition"]},
        {"x": 40, "y": 320, "w": 250, "h": 70, "style": "accent", "lines": ["partition 1: offsets 0, 1, 2 ...", "offset 0 is the slow job (C-SLOW)"]},
        {"x": 355, "y": 310, "w": 250, "h": 56, "style": "box", "lines": ["worker-1 holds offset 0", "one record, locked"]},
        {"x": 355, "y": 380, "w": 250, "h": 70, "style": "box", "lines": ["worker-2, worker-3 and worker-4", "take offsets 1, 2 ... from the same partition"]},
        {"x": 670, "y": 330, "w": 250, "h": 90, "style": "ink", "lines": ["later jobs finish before", "the slow job does", "no ordering guarantee"]},
    ],
    edges=[
        {"x1": 290, "y1": 135, "x2": 355, "y2": 135},
        {"x1": 605, "y1": 135, "x2": 670, "y2": 135, "dashed": True},
        {"x1": 290, "y1": 345, "x2": 355, "y2": 338, "amber": True},
        {"x1": 290, "y1": 365, "x2": 355, "y2": 410, "amber": True},
        {"x1": 605, "y1": 410, "x2": 670, "y2": 380, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One slow job: who waits behind it"},
    ],
)
