"""Event-type key: the replica that owns partition 2 stalls, so Delivered is applied before Scheduled."""

from generate_diagram import emit

emit(
    "04-stalled-partition",
    960,
    520,
    bands=[
        {"x": 20, "y": 190, "w": 920, "h": 100, "fill": "#fafafa", "label": "edp.shipping.shipments, keyed by event type"},
    ],
    nodes=[
        {"x": 330, "y": 40, "w": 300, "h": 60, "style": "ink", "lines": ["shipping-service", "publishes 1 Scheduled, 2 InTransit, 3 Delivered"]},
        {"x": 40, "y": 220, "w": 270, "h": 56, "style": "box", "lines": ["partition 0", "InTransit (2), Delivered (3)"]},
        {"x": 345, "y": 220, "w": 270, "h": 56, "style": "ghost", "lines": ["partition 1", "empty"]},
        {"x": 650, "y": 220, "w": 270, "h": 56, "style": "accent", "lines": ["partition 2", "Scheduled (1)"]},
        {"x": 40, "y": 340, "w": 270, "h": 56, "style": "box", "lines": ["order replica B", "applies InTransit, then Delivered"]},
        {"x": 650, "y": 340, "w": 270, "h": 56, "style": "ghost", "lines": ["order replica A: paused", "applies Scheduled after it resumes"]},
        {"x": 250, "y": 440, "w": 460, "h": 56, "style": "accent", "lines": ["order status: SHIPPED, DELIVERED, then SHIPPED", "last write wins, so the order ends SHIPPED"]},
    ],
    edges=[
        {"x1": 400, "y1": 100, "x2": 175, "y2": 220, "amber": True},
        {"x1": 560, "y1": 100, "x2": 785, "y2": 220, "amber": True},
        {"x1": 175, "y1": 276, "x2": 175, "y2": 340},
        {"x1": 785, "y1": 276, "x2": 785, "y2": 340, "dashed": True},
        {"x1": 175, "y1": 396, "x2": 360, "y2": 440},
        {"x1": 785, "y1": 396, "x2": 600, "y2": 440, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One stalled partition reorders an order that was published in sequence"},
    ],
)
