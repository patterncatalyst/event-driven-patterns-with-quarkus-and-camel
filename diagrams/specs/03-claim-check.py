"""Claim check: store the large part once, publish a small event with the claim, fetch on demand."""

from generate_diagram import emit

emit(
    "03-claim-check",
    1280,
    470,
    bands=[
        {"x": 20, "y": 50, "w": 1240, "h": 170, "fill": "#fafafa", "label": "Write: the attachment is stored, the event stays small"},
        {"x": 20, "y": 235, "w": 1240, "h": 180, "fill": "#fafafa", "label": "Read: only a consumer that needs the content asks for it"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 250, "h": 80, "style": "ink",
         "lines": ["order-service", "POST /orders with attachment"]},
        {"x": 350, "y": 95, "w": 250, "h": 80, "style": "accent",
         "lines": ["orders.claim_check", "id, order_id, content, created_at"]},
        {"x": 660, "y": 95, "w": 250, "h": 80, "style": "accent",
         "lines": ["edp.orders.placed", "Order, status, claim id and size"]},
        {"x": 970, "y": 95, "w": 250, "h": 80, "style": "box",
         "lines": ["Every consumer", "reads a few hundred bytes"]},
        {"x": 40, "y": 285, "w": 250, "h": 80, "style": "box",
         "lines": ["Caller", "GET /order-copies/O-1001/attachment"]},
        {"x": 350, "y": 285, "w": 250, "h": 80, "style": "box",
         "lines": ["notification-service", "reads the claim id from its copy"]},
        {"x": 660, "y": 285, "w": 250, "h": 80, "style": "ink",
         "lines": ["order-service", "GET /claims/{id}"]},
        {"x": 970, "y": 285, "w": 250, "h": 80, "style": "accent",
         "lines": ["The content", "100 KB, fetched once, when asked"]},
    ],
    edges=[
        {"x1": 290, "y1": 135, "x2": 350, "y2": 135, "amber": True},
        {"x1": 600, "y1": 135, "x2": 660, "y2": 135, "amber": True},
        {"x1": 910, "y1": 135, "x2": 970, "y2": 135},
        {"x1": 290, "y1": 325, "x2": 350, "y2": 325},
        {"x1": 600, "y1": 325, "x2": 660, "y2": 325},
        {"x1": 910, "y1": 325, "x2": 970, "y2": 325, "amber": True},
    ],
    notes=[
        {"x": 640, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Claim check: store, publish the claim, fetch on demand"},
        {"x": 640, "y": 445, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "The event no longer grows with the attachment; reading the content now needs the order-service to be up."},
    ],
)
