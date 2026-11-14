"""The order_saga states and the event or deadline on each edge; every edge is a conditional update on the state."""

from generate_diagram import emit

emit(
    "22-saga-state-machine",
    960,
    400,
    nodes=[
        {"x": 20, "y": 150, "w": 190, "h": 70, "style": "box", "lines": ["AWAITING_STOCK", "deadline set at placement"]},
        {"x": 330, "y": 150, "w": 190, "h": 70, "style": "accent", "lines": ["AWAITING_PAYMENT", "deadline set at reservation"]},
        {"x": 700, "y": 70, "w": 190, "h": 60, "style": "ink", "lines": ["COMPLETED", "order PAID"]},
        {"x": 700, "y": 250, "w": 190, "h": 60, "style": "ink", "lines": ["CANCELLED", "OrderCancelled published"]},
        {"x": 20, "y": 300, "w": 420, "h": 70, "style": "kernel", "lines": ["UPDATE order_saga SET state = new WHERE state = expected", "the loser of a race updates no row and changes nothing"]},
    ],
    edges=[
        {"x1": 210, "y1": 175, "x2": 330, "y2": 175, "amber": True, "label": "InventoryReserved", "ly": -10},
        {"x1": 520, "y1": 165, "x2": 700, "y2": 100, "amber": True, "label": "PaymentProcessed", "ly": -10},
        {"x1": 520, "y1": 205, "x2": 700, "y2": 265, "dashed": True, "label": "PaymentFailed or deadline", "ly": 24},
        {"x1": 115, "y1": 220, "x2": 700, "y2": 285, "dashed": True, "label": "InventoryInsufficient or deadline", "ly": 24},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One service owns the saga state, and every transition is guarded by it"},
    ],
)
