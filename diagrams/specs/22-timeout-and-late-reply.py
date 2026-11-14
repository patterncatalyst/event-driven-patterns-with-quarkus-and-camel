"""A timeline: the payment service does not answer, the sweeper cancels at the deadline, inventory releases, and the returning payment service refuses the late reservation; a dashed branch shows the guard switched off."""

from generate_diagram import emit

emit(
    "22-timeout-and-late-reply",
    1000,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 130, "fill": "#fff8ef", "label": "payment-service is stopped"},
    ],
    nodes=[
        {"x": 40, "y": 95, "w": 170, "h": 80, "style": "box", "lines": ["InventoryReserved", "saga: AWAITING_PAYMENT", "deadline set"]},
        {"x": 235, "y": 95, "w": 170, "h": 80, "style": "accent", "lines": ["Deadline passes", "sweeper: CANCELLED", "OrderCancelled written"]},
        {"x": 430, "y": 95, "w": 170, "h": 80, "style": "box", "lines": ["inventory releases", "stock is back", "reservation RELEASED"]},
        {"x": 625, "y": 215, "w": 170, "h": 80, "style": "user", "lines": ["payment-service returns", "reads cancellations", "then reservations"]},
        {"x": 820, "y": 215, "w": 160, "h": 80, "style": "accent", "lines": ["Payment refused", "no PaymentProcessed", "no money taken"]},
        {"x": 820, "y": 95, "w": 160, "h": 80, "style": "ghost", "lines": ["Guard off:", "payment taken for", "a cancelled order"]},
    ],
    edges=[
        {"x1": 210, "y1": 135, "x2": 235, "y2": 135},
        {"x1": 405, "y1": 135, "x2": 430, "y2": 135},
        {"x1": 600, "y1": 175, "x2": 625, "y2": 235},
        {"x1": 795, "y1": 255, "x2": 820, "y2": 255, "amber": True},
        {"x1": 795, "y1": 235, "x2": 900, "y2": 175, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A participant that can act after the saga gave up needs a countermeasure"},
        {"x": 500, "y": 335, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "The lock is set by OrderCancelled and checked before the payment is taken"},
    ],
)
