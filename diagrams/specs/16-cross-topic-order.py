"""ShipmentDelivered is applied before ShipmentInTransit because the topics are read independently: the guarded upsert keeps DELIVERED, the unguarded one moves the view back."""

from generate_diagram import emit

emit(
    "16-cross-topic-order",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 170, "fill": "#fff8ef", "label": "Forward-only guard (the default)"},
        {"x": 20, "y": 260, "w": 920, "h": 170, "fill": "#fafafa", "label": "PROJECTION_GUARD=off (demo code)"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 190, "h": 70, "style": "box", "lines": ["Applied: SCHEDULED", "row shows SHIPPED"]},
        {"x": 260, "y": 100, "w": 190, "h": 70, "style": "box", "lines": ["Applied: DELIVERED", "row shows DELIVERED"]},
        {"x": 480, "y": 100, "w": 200, "h": 70, "style": "accent", "lines": ["IN_TRANSIT arrives late", "rank 2 is not above 3"]},
        {"x": 710, "y": 100, "w": 210, "h": 70, "style": "ink", "lines": ["Row stays DELIVERED", "the statement updates nothing"]},
        {"x": 40, "y": 300, "w": 190, "h": 70, "style": "box", "lines": ["Applied: SCHEDULED", "row shows SHIPPED"]},
        {"x": 260, "y": 300, "w": 190, "h": 70, "style": "box", "lines": ["Applied: DELIVERED", "row shows DELIVERED"]},
        {"x": 480, "y": 300, "w": 200, "h": 70, "style": "ghost", "lines": ["IN_TRANSIT arrives late", "last writer wins"]},
        {"x": 710, "y": 300, "w": 210, "h": 70, "style": "ghost", "lines": ["Row regresses", "to IN_TRANSIT, SHIPPED"]},
    ],
    edges=[
        {"x1": 230, "y1": 135, "x2": 260, "y2": 135},
        {"x1": 450, "y1": 135, "x2": 480, "y2": 135},
        {"x1": 680, "y1": 135, "x2": 710, "y2": 135},
        {"x1": 230, "y1": 335, "x2": 260, "y2": 335},
        {"x1": 450, "y1": 335, "x2": 480, "y2": 335, "dashed": True},
        {"x1": 680, "y1": 335, "x2": 710, "y2": 335, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Six topics are read independently, so a later status can be applied first"},
    ],
)
