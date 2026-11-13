"""One ShipmentScheduled at 09:07 under tumbling slots (one list) and under hopping windows of the same size advancing by 5 minutes (three lists)."""

from generate_diagram import emit

emit(
    "21-tumbling-vs-hopping",
    960,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 140, "fill": "#fff8ef", "label": "Tumbling slots, 15 minutes: every record is in exactly one window"},
        {"x": 20, "y": 220, "w": 920, "h": 180, "fill": "#fafafa", "label": "Hopping windows, 15 minutes advancing by 5: overlapping windows"},
    ],
    nodes=[
        {"x": 50, "y": 100, "w": 270, "h": 70, "style": "accent", "lines": ["Slot 09:00 to 09:15", "holds the 09:07 shipment", "count 1"]},
        {"x": 345, "y": 100, "w": 270, "h": 70, "style": "ghost", "lines": ["Slot 09:15 to 09:30", "empty"]},
        {"x": 640, "y": 100, "w": 270, "h": 70, "style": "ghost", "lines": ["Slot 09:30 to 09:45", "empty"]},
        {"x": 50, "y": 260, "w": 270, "h": 56, "style": "box", "lines": ["08:55 to 09:10", "holds the 09:07 shipment"]},
        {"x": 345, "y": 260, "w": 270, "h": 56, "style": "box", "lines": ["09:00 to 09:15", "holds the 09:07 shipment"]},
        {"x": 640, "y": 260, "w": 270, "h": 56, "style": "box", "lines": ["09:05 to 09:20", "holds the 09:07 shipment"]},
        {"x": 50, "y": 335, "w": 860, "h": 50, "style": "ink", "lines": ["one shipment is in 3 lists (size divided by advance): the warehouse would collect it three times"]},
    ],
    edges=[],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A pickup slot is a tumbling window: each shipment belongs to one list"},
    ],
)
