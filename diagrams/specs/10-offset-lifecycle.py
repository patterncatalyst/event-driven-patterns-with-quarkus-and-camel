"""The states a connector passes through when its offsets are read, altered or reset."""

from generate_diagram import emit

emit(
    "10-offset-lifecycle",
    960,
    420,
    nodes=[
        {"x": 40, "y": 160, "w": 190, "h": 80, "style": "accent", "lines": ["RUNNING", "GET offsets works"]},
        {"x": 385, "y": 160, "w": 190, "h": 80, "style": "box", "lines": ["STOPPED", "no tasks, slot inactive"]},
        {"x": 730, "y": 40, "w": 190, "h": 80, "style": "box", "lines": ["PATCH offsets", "alter: skip forward"]},
        {"x": 730, "y": 280, "w": 190, "h": 80, "style": "box", "lines": ["DELETE offsets", "reset: snapshot again"]},
        {"x": 40, "y": 320, "w": 400, "h": 60, "style": "ink", "lines": ["PATCH or DELETE while RUNNING", "HTTP 400: must be STOPPED"]},
    ],
    edges=[
        {"x1": 230, "y1": 190, "x2": 385, "y2": 190, "amber": True},
        {"x1": 575, "y1": 175, "x2": 730, "y2": 80},
        {"x1": 575, "y1": 225, "x2": 730, "y2": 320},
        {"x1": 730, "y1": 100, "x2": 230, "y2": 170, "dashed": True},
        {"x1": 730, "y1": 340, "x2": 230, "y2": 230, "dashed": True},
        {"x1": 135, "y1": 240, "x2": 135, "y2": 320, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Stop first, change the offset, then resume"},
        {"x": 305, "y": 178, "anchor": "middle", "size": 12, "color": "#444444", "text": "PUT /stop"},
        {"x": 480, "y": 130, "anchor": "middle", "size": 12, "color": "#444444", "text": "PUT /resume afterwards"},
    ],
)
