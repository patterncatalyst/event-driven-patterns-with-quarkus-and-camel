"""Three services and the channels between them: each an endpoint that consumes one event and publishes the next, the happy path forward, the compensation path back."""

from generate_diagram import emit

emit(
    "22-saga-choreography",
    970,
    580,
    bands=[
        {"x": 20, "y": 165, "w": 210, "h": 280, "fill": "#fafafa", "label": "order-service"},
        {"x": 380, "y": 165, "w": 210, "h": 280, "fill": "#fafafa", "label": "inventory-service"},
        {"x": 740, "y": 165, "w": 210, "h": 280, "fill": "#fafafa", "label": "payment-service"},
    ],
    nodes=[
        {"x": 25, "y": 315, "w": 200, "h": 70, "style": "accent", "lines": ["order-service", "order_saga, deadline and sweeper"]},
        {"x": 385, "y": 315, "w": 200, "h": 70, "style": "box", "lines": ["inventory-service", "reservation, release is idempotent"]},
        {"x": 745, "y": 315, "w": 200, "h": 70, "style": "box", "lines": ["payment-service", "refuses a cancelled order"]},
    ],
    icons=[
        {"x": 25, "y": 195, "w": 200, "h": 90, "kind": "endpoint", "label": "react, publish orders.placed"},
        {"x": 385, "y": 195, "w": 200, "h": 90, "kind": "endpoint", "label": "consume, reserve stock"},
        {"x": 745, "y": 195, "w": 200, "h": 90, "kind": "endpoint", "label": "consume, attempt payment"},
        {"x": 240, "y": 200, "w": 130, "h": 85, "kind": "channel", "label": "edp.orders.placed"},
        {"x": 600, "y": 200, "w": 130, "h": 85, "kind": "channel", "label": "edp.inventory.reserved"},
        {"x": 650, "y": 55, "w": 110, "h": 90, "kind": "channel", "label": "edp.payments.processed"},
        {"x": 270, "y": 460, "w": 110, "h": 90, "kind": "channel", "label": "edp.orders.cancelled"},
    ],
    edges=[
        {"x1": 225, "y1": 240, "x2": 240, "y2": 242},
        {"x1": 370, "y1": 242, "x2": 385, "y2": 240},
        {"x1": 585, "y1": 240, "x2": 600, "y2": 242},
        {"x1": 730, "y1": 242, "x2": 745, "y2": 240},
        {"x1": 845, "y1": 195, "x2": 705, "y2": 145, "amber": True},
        {"x1": 650, "y1": 100, "x2": 125, "y2": 195, "amber": True, "label": "stops the sweeper", "ly": -10},
        {"x1": 845, "y1": 285, "x2": 585, "y2": 285, "dashed": True, "label": "payments.failed", "ly": 16},
        {"x1": 385, "y1": 285, "x2": 225, "y2": 285, "dashed": True, "label": "inventory.insufficient", "ly": 16},
        {"x1": 125, "y1": 385, "x2": 325, "y2": 460, "dashed": True},
        {"x1": 270, "y1": 505, "x2": 485, "y2": 385, "dashed": True},
        {"x1": 380, "y1": 505, "x2": 845, "y2": 385, "dashed": True},
    ],
    notes=[
        {"x": 475, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "No coordinator: each service reacts to the previous event and publishes its own"},
    ],
)
