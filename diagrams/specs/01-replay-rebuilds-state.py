"""Command, check, append, replay: how the event model answers both 'now' and 'then'."""

from generate_diagram import emit

emit(
    "01-replay-rebuilds-state",
    960,
    400,
    bands=[
        {"x": 20, "y": 20, "w": 920, "h": 175, "fill": "#fafafa", "label": "Write: command, check, append"},
        {"x": 20, "y": 215, "w": 920, "h": 165, "fill": "#fafafa", "label": "Read: fold the stream"},
    ],
    nodes=[
        {"x": 40, "y": 70, "w": 190, "h": 80, "style": "ink",
         "lines": ["ChangeConsignmentStatus", "to CLEARED"]},
        {"x": 280, "y": 70, "w": 190, "h": 80, "style": "box",
         "lines": ["Replay the stream", "current status IN_TRANSIT"]},
        {"x": 520, "y": 70, "w": 190, "h": 80, "style": "accent",
         "lines": ["Check the transition", "IN_TRANSIT to CLEARED allowed"]},
        {"x": 760, "y": 70, "w": 160, "h": 80, "style": "box",
         "lines": ["Append v4", "ConsignmentStatusChanged"]},
        {"x": 40, "y": 265, "w": 190, "h": 80, "style": "box",
         "lines": ["v1 ConsignmentBooked", "status BOOKED"]},
        {"x": 280, "y": 265, "w": 190, "h": 80, "style": "box",
         "lines": ["v2 StatusChanged", "status LOADED"]},
        {"x": 520, "y": 265, "w": 190, "h": 80, "style": "box",
         "lines": ["v3 StatusChanged", "status IN_TRANSIT"]},
        {"x": 760, "y": 265, "w": 160, "h": 80, "style": "accent",
         "lines": ["ConsignmentDocument", "same as the row"]},
    ],
    edges=[
        {"x1": 230, "y1": 110, "x2": 280, "y2": 110},
        {"x1": 470, "y1": 110, "x2": 520, "y2": 110},
        {"x1": 710, "y1": 110, "x2": 760, "y2": 110},
        {"x1": 230, "y1": 305, "x2": 280, "y2": 305},
        {"x1": 470, "y1": 305, "x2": 520, "y2": 305},
        {"x1": 710, "y1": 305, "x2": 760, "y2": 305, "amber": True},
    ],
)
