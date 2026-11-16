"""A timeline: 13 to 15 are released at once, 16 is lost, 17 and 18 wait for the timeout, and a late 16 is applied last."""

from generate_diagram import emit

emit(
    "ae-gap-timeout",
    1000,
    340,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 120, "fill": "#fff8ef", "label": "16 never arrives in time"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 170, "h": 70, "style": "box", "lines": ["13, 14, 15 arrive", "each is the successor", "of the one before"]},
        {"x": 235, "y": 95, "w": 170, "h": 70, "style": "ink", "lines": ["Applied at once", "13, 14, 15"]},
        {"x": 430, "y": 95, "w": 170, "h": 70, "style": "box", "lines": ["17, 18 arrive", "the successor of 15", "is 16: they wait"]},
        {"x": 625, "y": 95, "w": 170, "h": 70, "style": "accent", "lines": ["Timeout passes", "17, 18 applied", "the gap is skipped"]},
        {"x": 820, "y": 95, "w": 160, "h": 70, "style": "kernel", "lines": ["Nothing reports", "that 16 is missing"]},
        {"x": 430, "y": 235, "w": 170, "h": 70, "style": "user", "lines": ["16 arrives late"]},
        {"x": 625, "y": 235, "w": 170, "h": 70, "style": "ghost", "lines": ["rejectOff: applied last", "13, 14, 15, 17, 18, 16"]},
        {"x": 820, "y": 235, "w": 160, "h": 70, "style": "accent", "lines": ["rejectOld: refused", "CamelExchangeException"]},
    ],
    edges=[
        {"x1": 210, "y1": 130, "x2": 235, "y2": 130},
        {"x1": 405, "y1": 130, "x2": 430, "y2": 130},
        {"x1": 600, "y1": 130, "x2": 625, "y2": 130, "amber": True},
        {"x1": 795, "y1": 130, "x2": 820, "y2": 130, "dashed": True},
        {"x1": 600, "y1": 270, "x2": 625, "y2": 270, "dashed": True},
        {"x1": 795, "y1": 270, "x2": 820, "y2": 270, "dashed": True},
    ],
)
