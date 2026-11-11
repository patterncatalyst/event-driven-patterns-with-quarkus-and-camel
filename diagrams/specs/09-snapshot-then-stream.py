"""The initial snapshot, then streaming from the slot, with an incremental snapshot read in chunks alongside."""

from generate_diagram import emit

emit(
    "09-snapshot-then-stream",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 120, "fill": "#fafafa", "label": "Connector"},
        {"x": 20, "y": 200, "w": 920, "h": 120, "fill": "#fafafa", "label": "Signal table"},
        {"x": 20, "y": 340, "w": 920, "h": 90, "fill": "#fafafa", "label": "Documents topic"},
    ],
    nodes=[
        {"x": 40, "y": 92, "w": 230, "h": 70, "style": "box", "lines": ["initial snapshot", "5 r events, one per row"]},
        {"x": 310, "y": 92, "w": 300, "h": 70, "style": "accent", "lines": ["streaming from the slot", "c, u and d events in log order"]},
        {"x": 650, "y": 92, "w": 270, "h": 70, "style": "box", "lines": ["streaming continues", "r events interleave with changes"]},
        {"x": 310, "y": 232, "w": 300, "h": 70, "style": "user", "lines": ["insert execute-snapshot", "data-collections: inventory_item"]},
        {"x": 650, "y": 232, "w": 270, "h": 70, "style": "box", "lines": ["incremental snapshot", "rows read in chunks, by primary key"]},
        {"x": 40, "y": 362, "w": 880, "h": 54, "style": "ink", "lines": ["one document per SKU, latest wins", "a row read during a chunk loses to a change streamed in the same window"]},
    ],
    edges=[
        {"x1": 270, "y1": 127, "x2": 310, "y2": 127, "amber": True},
        {"x1": 610, "y1": 127, "x2": 650, "y2": 127, "amber": True},
        {"x1": 460, "y1": 232, "x2": 460, "y2": 162, "dashed": True},
        {"x1": 610, "y1": 267, "x2": 650, "y2": 267, "dashed": True},
        {"x1": 785, "y1": 232, "x2": 785, "y2": 162, "dashed": True},
        {"x1": 155, "y1": 162, "x2": 155, "y2": 362, "dashed": True},
        {"x1": 900, "y1": 162, "x2": 900, "y2": 362, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A snapshot gives the table's state once; the log keeps it current; a signal reads it again"},
    ],
)
