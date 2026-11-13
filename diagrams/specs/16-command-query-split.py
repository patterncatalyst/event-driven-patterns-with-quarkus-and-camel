"""Three services publish their events to six topics; the query side applies them to one view that readers query, and the lag window sits between the topics and the view."""

from generate_diagram import emit

emit(
    "16-command-query-split",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 230, "h": 370, "fill": "#fafafa", "label": "Write models, one per owner"},
        {"x": 280, "y": 60, "w": 210, "h": 370, "fill": "#fafafa", "label": "Public topics, one partition each"},
        {"x": 520, "y": 60, "w": 420, "h": 370, "fill": "#fff8ef", "label": "order-service, query side"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 190, "h": 70, "style": "box", "lines": ["order service", "events and outbox"]},
        {"x": 40, "y": 220, "w": 190, "h": 70, "style": "box", "lines": ["payment service", "payments"]},
        {"x": 40, "y": 340, "w": 190, "h": 70, "style": "box", "lines": ["shipping service", "shipments"]},
        {"x": 300, "y": 100, "w": 170, "h": 70, "style": "kernel", "lines": ["edp.orders.placed", "edp.orders.cancelled"]},
        {"x": 300, "y": 220, "w": 170, "h": 70, "style": "kernel", "lines": ["edp.payments.processed"]},
        {"x": 300, "y": 340, "w": 170, "h": 70, "style": "kernel", "lines": ["edp.shipping.scheduled", "in-transit, delivered"]},
        {"x": 560, "y": 100, "w": 340, "h": 70, "style": "accent", "lines": ["OrderViewProjector", "one guarded upsert per event"]},
        {"x": 560, "y": 220, "w": 340, "h": 70, "style": "kernel", "lines": ["orders.order_view", "one row per order, each column from its owner"]},
        {"x": 560, "y": 340, "w": 340, "h": 70, "style": "ink", "lines": ["GET /orders/{id}", "GET /customers/{id}/orders, with asOf"]},
    ],
    edges=[
        {"x1": 230, "y1": 135, "x2": 300, "y2": 135},
        {"x1": 230, "y1": 255, "x2": 300, "y2": 255},
        {"x1": 230, "y1": 375, "x2": 300, "y2": 375},
        {"x1": 470, "y1": 135, "x2": 560, "y2": 135, "amber": True},
        {"x1": 470, "y1": 255, "x2": 560, "y2": 135, "amber": True},
        {"x1": 470, "y1": 375, "x2": 560, "y2": 135, "amber": True},
        {"x1": 730, "y1": 170, "x2": 730, "y2": 220},
        {"x1": 730, "y1": 290, "x2": 730, "y2": 340},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Writers publish, the projector applies, readers query only the view"},
        {"x": 515, "y": 190, "anchor": "middle", "size": 12, "color": "#b45309", "text": "lag window"},
    ],
)
