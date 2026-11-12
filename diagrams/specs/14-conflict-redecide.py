"""Two timelines for a cancellation that loses a race to a shipment: decided again it is refused, re-appended blind it lands after the shipment."""

from generate_diagram import emit

emit(
    "14-conflict-redecide",
    960,
    560,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 220, "fill": "#fff8ef", "label": "Decide again after a conflict"},
        {"x": 20, "y": 300, "w": 920, "h": 220, "fill": "#fafafa", "label": "Append the old decision at the new head"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 70, "style": "box", "lines": ["Cancel loads v2", "status PAID", "decides OrderCancelled"]},
        {"x": 270, "y": 100, "w": 200, "h": 70, "style": "box", "lines": ["Shipment appends v3", "OrderShipped commits first"]},
        {"x": 500, "y": 100, "w": 200, "h": 70, "style": "accent", "lines": ["Cancel appends v3", "unique constraint: conflict"]},
        {"x": 730, "y": 100, "w": 190, "h": 70, "style": "accent", "lines": ["Cancel loads v3 again", "SHIPPED to CANCELLED"]},
        {"x": 500, "y": 190, "w": 420, "h": 56, "style": "ink", "lines": ["Answer 409, no cancelled record", "the stream stays at versions 1 to 3"]},
        {"x": 40, "y": 340, "w": 200, "h": 70, "style": "box", "lines": ["Cancel loads v2", "status PAID", "decides OrderCancelled"]},
        {"x": 270, "y": 340, "w": 200, "h": 70, "style": "box", "lines": ["Shipment appends v3", "OrderShipped commits first"]},
        {"x": 500, "y": 340, "w": 200, "h": 70, "style": "ghost", "lines": ["Cancel appends v3", "conflict"]},
        {"x": 730, "y": 340, "w": 190, "h": 70, "style": "ghost", "lines": ["Cancel appends v4", "the decision is not checked"]},
        {"x": 500, "y": 430, "w": 420, "h": 56, "style": "ink", "lines": ["OrderCancelled after OrderShipped", "and a cancelled record on Kafka"]},
    ],
    edges=[
        {"x1": 240, "y1": 135, "x2": 270, "y2": 135},
        {"x1": 470, "y1": 135, "x2": 500, "y2": 135},
        {"x1": 700, "y1": 135, "x2": 730, "y2": 135},
        {"x1": 825, "y1": 170, "x2": 825, "y2": 190},
        {"x1": 240, "y1": 375, "x2": 270, "y2": 375},
        {"x1": 470, "y1": 375, "x2": 500, "y2": 375},
        {"x1": 700, "y1": 375, "x2": 730, "y2": 375, "dashed": True},
        {"x1": 825, "y1": 410, "x2": 825, "y2": 430, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A retry reloads and decides again, or the caller learns of the conflict"},
    ],
)
