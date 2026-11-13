"""A timeline: ShipmentDelivered is produced while its channel is paused, reads stay SHIPPED with an older asOf, and after resume the view converges and the lag timer records the gap."""

from generate_diagram import emit

emit(
    "16-read-lag-window",
    960,
    360,
    bands=[
        {"x": 250, "y": 60, "w": 450, "h": 140, "fill": "#fff8ef", "label": "The delivered channel is paused"},
    ],
    nodes=[
        {"x": 20, "y": 100, "w": 200, "h": 80, "style": "box", "lines": ["ShipmentDelivered", "produced at t0", "group lag becomes 1"]},
        {"x": 270, "y": 100, "w": 190, "h": 80, "style": "accent", "lines": ["Read at t0 + 1 s", "SHIPPED", "asOf: scheduled time"]},
        {"x": 490, "y": 100, "w": 190, "h": 80, "style": "accent", "lines": ["Read at t0 + 4 s", "SHIPPED", "asOf unchanged"]},
        {"x": 720, "y": 100, "w": 220, "h": 80, "style": "box", "lines": ["Resume", "upsert applied, record", "acknowledged at t1"]},
        {"x": 720, "y": 210, "w": 220, "h": 70, "style": "ink", "lines": ["Read after t1", "DELIVERED, asOf is the", "delivered record time"]},
        {"x": 20, "y": 210, "w": 680, "h": 70, "style": "kernel", "lines": ["order.view.lag records t1 minus the record timestamp of ShipmentDelivered", "the paused span appears as the maximum"]},
    ],
    edges=[
        {"x1": 220, "y1": 140, "x2": 270, "y2": 140},
        {"x1": 460, "y1": 140, "x2": 490, "y2": 140},
        {"x1": 680, "y1": 140, "x2": 720, "y2": 140},
        {"x1": 830, "y1": 180, "x2": 830, "y2": 210},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A read answers from what the view has applied, and says when that was"},
    ],
)
