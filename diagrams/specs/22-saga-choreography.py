"""Three services and the catalog topics between them: the happy path forward, the compensation path back."""

from generate_diagram import emit

emit(
    "22-saga-choreography",
    860,
    390,
    nodes=[
        {"x": 300, "y": 50, "w": 260, "h": 40, "style": "ink", "lines": ["edp.payments.processed"]},
        {"x": 20, "y": 130, "w": 170, "h": 100, "style": "accent", "lines": ["order-service", "order_saga", "deadline and sweeper"]},
        {"x": 345, "y": 130, "w": 170, "h": 100, "style": "box", "lines": ["inventory-service", "reservation", "release is idempotent"]},
        {"x": 670, "y": 130, "w": 170, "h": 100, "style": "box", "lines": ["payment-service", "payment, cancelled_order", "refuses a cancelled order"]},
        {"x": 300, "y": 310, "w": 260, "h": 40, "style": "ink", "lines": ["edp.orders.cancelled"]},
    ],
    edges=[
        {"x1": 190, "y1": 160, "x2": 345, "y2": 160, "amber": True, "label": "orders.placed", "ly": -10},
        {"x1": 515, "y1": 160, "x2": 670, "y2": 160, "amber": True, "label": "inventory.reserved", "ly": -10},
        {"x1": 755, "y1": 130, "x2": 560, "y2": 80, "amber": True},
        {"x1": 300, "y1": 70, "x2": 105, "y2": 130, "amber": True},
        {"x1": 670, "y1": 205, "x2": 515, "y2": 205, "dashed": True, "label": "payments.failed", "ly": 16},
        {"x1": 345, "y1": 205, "x2": 190, "y2": 205, "dashed": True, "label": "inventory.insufficient", "ly": 16},
        {"x1": 105, "y1": 230, "x2": 300, "y2": 320, "dashed": True},
        {"x1": 430, "y1": 310, "x2": 430, "y2": 230, "dashed": True},
        {"x1": 560, "y1": 330, "x2": 755, "y2": 230, "dashed": True},
    ],
    notes=[
        {"x": 430, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "No coordinator: each service reacts to the previous event and publishes its own"},
    ],
)
