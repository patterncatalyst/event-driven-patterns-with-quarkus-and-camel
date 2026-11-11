"""A per-order buffer in the database: held events 2 and 3 are applied in order once 1 arrives."""

from generate_diagram import emit

emit(
    "04-resequencer",
    960,
    470,
    nodes=[
        {"x": 40, "y": 60, "w": 240, "h": 70, "style": "box", "lines": ["order replica B", "receives 2 InTransit, 3 Delivered"]},
        {"x": 40, "y": 270, "w": 240, "h": 70, "style": "ghost", "lines": ["order replica A", "receives 1 Scheduled after it resumes"]},
        {"x": 350, "y": 150, "w": 250, "h": 100, "style": "box", "lines": ["pending_shipment_event", "unique (order_id, sequence)", "holds 2 and 3 for this order"]},
        {"x": 350, "y": 300, "w": 250, "h": 70, "style": "accent", "lines": ["order row, locked", "next_sequence = 1"]},
        {"x": 670, "y": 225, "w": 250, "h": 90, "style": "ink", "lines": ["apply 1, 2, 3 in one transaction", "delete each; next_sequence = 4", "status ends DELIVERED"]},
    ],
    edges=[
        {"x1": 280, "y1": 95, "x2": 350, "y2": 185, "amber": True},
        {"x1": 280, "y1": 305, "x2": 350, "y2": 335, "dashed": True},
        {"x1": 475, "y1": 250, "x2": 475, "y2": 300},
        {"x1": 600, "y1": 335, "x2": 670, "y2": 285, "amber": True},
        {"x1": 600, "y1": 200, "x2": 670, "y2": 250, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Each order's events wait in the database until their turn"},
        {"x": 480, "y": 430, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "If 1 never arrives, 2 and 3 stay held. Other orders are not affected."},
    ],
)
