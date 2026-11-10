"""Command, check, append, replay: how the event model answers both 'now' and 'then'."""

from generate_diagram import emit

emit(
    "01-replay-rebuilds-state",
    1280,
    410,
    bands=[
        {"x": 20, "y": 50, "w": 1240, "h": 140, "fill": "#fafafa", "label": "Write: stream holds v1 to v3, command appends v4"},
        {"x": 20, "y": 205, "w": 1240, "h": 140, "fill": "#fafafa", "label": "Read: stream holds v1 to v4, replay folds it"},
    ],
    nodes=[
        {"x": 40, "y": 90, "w": 250, "h": 80, "style": "ink",
         "lines": ["ChangeConsignmentStatus", "to CLEARED"]},
        {"x": 350, "y": 90, "w": 250, "h": 80, "style": "box",
         "lines": ["Replay v1 to v3", "current status IN_TRANSIT"]},
        {"x": 660, "y": 90, "w": 250, "h": 80, "style": "accent",
         "lines": ["Check the transition", "IN_TRANSIT to CLEARED"]},
        {"x": 970, "y": 90, "w": 250, "h": 80, "style": "box",
         "lines": ["Append v4", "ConsignmentStatusChanged"]},
        {"x": 40, "y": 245, "w": 250, "h": 80, "style": "box",
         "lines": ["v1 ConsignmentBooked", "status BOOKED"]},
        {"x": 350, "y": 245, "w": 250, "h": 80, "style": "box",
         "lines": ["v2 to v4 StatusChanged", "ConsignmentStatusChanged x3"]},
        {"x": 660, "y": 245, "w": 250, "h": 80, "style": "accent",
         "lines": ["Fold left", "status after v4: CLEARED"]},
        {"x": 970, "y": 245, "w": 250, "h": 80, "style": "accent",
         "lines": ["ConsignmentDocument", "same as the row"]},
    ],
    edges=[
        {"x1": 290, "y1": 130, "x2": 350, "y2": 130},
        {"x1": 600, "y1": 130, "x2": 660, "y2": 130},
        {"x1": 910, "y1": 130, "x2": 970, "y2": 130},
        {"x1": 290, "y1": 285, "x2": 350, "y2": 285},
        {"x1": 600, "y1": 285, "x2": 660, "y2": 285},
        {"x1": 910, "y1": 285, "x2": 970, "y2": 285, "amber": True},
    ],
    notes=[
        {"x": 640, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Check against the replayed state, append, fold to read"},
        {"x": 640, "y": 380, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "A command never edits history: it appends one fact, and every reader folds the same stream."},
    ],
)
