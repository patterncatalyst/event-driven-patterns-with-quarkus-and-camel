"""One command, two storage outcomes: the row overwrites its status, the stream appends a fact."""

from generate_diagram import emit

emit(
    "01-row-update-vs-event-append",
    960,
    470,
    bands=[
        {"x": 20, "y": 150, "w": 450, "h": 300, "fill": "#fafafa", "label": "Row model: consignment"},
        {"x": 490, "y": 150, "w": 450, "h": 300, "fill": "#fafafa", "label": "Event model: consignment_event"},
    ],
    nodes=[
        {"x": 280, "y": 15, "w": 400, "h": 70, "style": "ink",
         "lines": ["ChangeConsignmentStatus", "consignmentId C-1001, to IN_TRANSIT"]},
        {"x": 50, "y": 185, "w": 390, "h": 60, "style": "box",
         "lines": ["Before: status LOADED", "one row, id C-1001"]},
        {"x": 50, "y": 285, "w": 390, "h": 60, "style": "accent",
         "lines": ["UPDATE consignment SET status = IN_TRANSIT", "in place"]},
        {"x": 50, "y": 375, "w": 390, "h": 60, "style": "box",
         "lines": ["After: status IN_TRANSIT", "LOADED is gone"]},
        {"x": 520, "y": 185, "w": 390, "h": 60, "style": "box",
         "lines": ["Before: v1 ConsignmentBooked, v2 LOADED", "two facts in stream C-1001"]},
        {"x": 520, "y": 285, "w": 390, "h": 60, "style": "accent",
         "lines": ["INSERT version 3 ConsignmentStatusChanged", "unique (stream_id, version)"]},
        {"x": 520, "y": 375, "w": 390, "h": 60, "style": "box",
         "lines": ["After: v1, v2, v3", "every earlier status is still there"]},
    ],
    edges=[
        {"x1": 400, "y1": 85, "x2": 245, "y2": 150, "amber": True},
        {"x1": 560, "y1": 85, "x2": 715, "y2": 150, "amber": True},
        {"x1": 245, "y1": 245, "x2": 245, "y2": 285},
        {"x1": 245, "y1": 345, "x2": 245, "y2": 375},
        {"x1": 715, "y1": 245, "x2": 715, "y2": 285},
        {"x1": 715, "y1": 345, "x2": 715, "y2": 375},
    ],
)
