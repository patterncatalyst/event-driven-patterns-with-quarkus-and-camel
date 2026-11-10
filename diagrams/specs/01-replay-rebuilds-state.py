"""Command, check, append, replay: how the event model answers both 'now' and 'then'."""

from generate_diagram import emit

emit(
    "01-replay-rebuilds-state",
    1280,
    420,
    bands=[
        {"x": 20, "y": 20, "w": 1240, "h": 180, "fill": "#fafafa", "label": "Write: stream holds v1 to v3, command appends v4"},
        {"x": 20, "y": 220, "w": 1240, "h": 180, "fill": "#fafafa", "label": "Read: stream holds v1 to v4, replay folds it"},
    ],
    nodes=[
        {"x": 40, "y": 70, "w": 250, "h": 80, "style": "ink",
         "lines": ["ChangeConsignmentStatus", "to CLEARED"]},
        {"x": 350, "y": 70, "w": 250, "h": 80, "style": "box",
         "lines": ["Replay v1 to v3", "current status IN_TRANSIT"]},
        {"x": 660, "y": 70, "w": 250, "h": 80, "style": "accent",
         "lines": ["Check the transition", "IN_TRANSIT to CLEARED"]},
        {"x": 970, "y": 70, "w": 250, "h": 80, "style": "box",
         "lines": ["Append v4", "ConsignmentStatusChanged"]},
        {"x": 40, "y": 270, "w": 250, "h": 80, "style": "box",
         "lines": ["v1 ConsignmentBooked", "status BOOKED"]},
        {"x": 350, "y": 270, "w": 250, "h": 80, "style": "box",
         "lines": ["v2 to v4 StatusChanged", "ConsignmentStatusChanged x3"]},
        {"x": 660, "y": 270, "w": 250, "h": 80, "style": "accent",
         "lines": ["Fold left", "status after v4: CLEARED"]},
        {"x": 970, "y": 270, "w": 250, "h": 80, "style": "accent",
         "lines": ["ConsignmentDocument", "same as the row"]},
    ],
    edges=[
        {"x1": 290, "y1": 110, "x2": 350, "y2": 110},
        {"x1": 600, "y1": 110, "x2": 660, "y2": 110},
        {"x1": 910, "y1": 110, "x2": 970, "y2": 110},
        {"x1": 290, "y1": 310, "x2": 350, "y2": 310},
        {"x1": 600, "y1": 310, "x2": 660, "y2": 310},
        {"x1": 910, "y1": 310, "x2": 970, "y2": 310, "amber": True},
    ],
)
