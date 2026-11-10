"""The order lifecycle with its cancellation exits, and the shipment lifecycle beside it."""

from generate_diagram import emit

emit(
    "00-order-lifecycle",
    1040,
    430,
    bands=[
        {"x": 20, "y": 50, "w": 1000, "h": 190, "fill": "#fafafa", "label": "Order"},
        {"x": 20, "y": 255, "w": 1000, "h": 110, "fill": "#fafafa", "label": "Shipment"},
    ],
    nodes=[
        {"x": 40, "y": 90, "w": 150, "h": 56, "style": "accent", "lines": ["PLACED"]},
        {"x": 290, "y": 90, "w": 150, "h": 56, "style": "box", "lines": ["PAID"]},
        {"x": 540, "y": 90, "w": 150, "h": 56, "style": "box", "lines": ["SHIPPED"]},
        {"x": 790, "y": 90, "w": 150, "h": 56, "style": "ink", "lines": ["DELIVERED", "terminal"]},
        {"x": 165, "y": 175, "w": 150, "h": 50, "style": "ghost", "lines": ["CANCELLED", "terminal"]},
        {"x": 40, "y": 295, "w": 150, "h": 56, "style": "box", "lines": ["PENDING"]},
        {"x": 290, "y": 295, "w": 150, "h": 56, "style": "box", "lines": ["SCHEDULED"]},
        {"x": 540, "y": 295, "w": 150, "h": 56, "style": "box", "lines": ["IN_TRANSIT"]},
        {"x": 790, "y": 295, "w": 150, "h": 56, "style": "ink", "lines": ["DELIVERED"]},
    ],
    edges=[
        {"x1": 190, "y1": 118, "x2": 290, "y2": 118, "amber": True},
        {"x1": 440, "y1": 118, "x2": 540, "y2": 118, "amber": True},
        {"x1": 690, "y1": 118, "x2": 790, "y2": 118, "amber": True},
        {"x1": 115, "y1": 146, "x2": 190, "y2": 175, "dashed": True},
        {"x1": 365, "y1": 146, "x2": 290, "y2": 175, "dashed": True},
        {"x1": 190, "y1": 323, "x2": 290, "y2": 323},
        {"x1": 440, "y1": 323, "x2": 540, "y2": 323},
        {"x1": 690, "y1": 323, "x2": 790, "y2": 323},
    ],
    notes=[
        {"x": 520, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One step forward at a time; cancel only before the parcel ships"},
        {"x": 520, "y": 400, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "A shipment starts when payment succeeds, and its delivery moves the order to DELIVERED."},
    ],
)
