"""How a source group offset becomes a target group offset, and where the consumer resumes."""

from generate_diagram import emit

emit(
    "34-offset-translation",
    1000,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 130, "fill": "#f6f8fa", "label": "Source cluster edp"},
        {"x": 20, "y": 215, "w": 960, "h": 150, "fill": "#fff8ef", "label": "Target cluster edp-dr"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 210, "h": 70, "style": "box", "lines": ["Record at source offset 4", "partition 1"]},
        {"x": 290, "y": 100, "w": 210, "h": 70, "style": "accent", "lines": ["Offset sync", "source 4 is target 4", "offset.lag.max: 0"]},
        {"x": 540, "y": 100, "w": 210, "h": 70, "style": "box", "lines": ["Group commits 4", "then is stopped"]},
        {"x": 790, "y": 100, "w": 170, "h": 70, "style": "accent", "lines": ["Checkpoint", "group, partition,", "translated offset"]},
        {"x": 40, "y": 255, "w": 210, "h": 80, "style": "ink", "lines": ["Copied record", "target offset 4"]},
        {"x": 540, "y": 255, "w": 210, "h": 80, "style": "accent", "lines": ["Group on edp-dr", "committed 4", "(only while it has no members)"]},
        {"x": 790, "y": 255, "w": 170, "h": 80, "style": "user", "lines": ["Consumer, new", "bootstrap address", "reads from 4"]},
    ],
    edges=[
        {"x1": 250, "y1": 135, "x2": 290, "y2": 135, "dashed": True},
        {"x1": 500, "y1": 135, "x2": 540, "y2": 135, "dashed": True},
        {"x1": 750, "y1": 135, "x2": 790, "y2": 135, "amber": True},
        {"x1": 875, "y1": 170, "x2": 700, "y2": 255, "amber": True, "label": "sync every 5 s", "ly": -8},
        {"x1": 750, "y1": 295, "x2": 790, "y2": 295, "amber": True},
        {"x1": 395, "y1": 170, "x2": 250, "y2": 255, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The target group is written from a checkpoint, so the consumer needs no offset of its own"},
        {"x": 500, "y": 395, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "Without the sync setting no group exists on the target, and the consumer starts at its reset policy"},
    ],
)
