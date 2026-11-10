"""The five services of the running example and the topics that connect them."""

from generate_diagram import emit

emit(
    "00-running-example",
    1090,
    420,
    nodes=[
        {"x": 20, "y": 70, "w": 150, "h": 100, "style": "accent", "lines": ["order-service", "Order"]},
        {"x": 320, "y": 70, "w": 150, "h": 100, "style": "box", "lines": ["inventory-service", "InventoryItem"]},
        {"x": 620, "y": 70, "w": 150, "h": 100, "style": "box", "lines": ["payment-service", "Payment"]},
        {"x": 920, "y": 70, "w": 150, "h": 100, "style": "box", "lines": ["shipping-service", "Shipment"]},
        {"x": 20, "y": 290, "w": 1050, "h": 60, "style": "ink",
         "lines": ["notification-service", "reads every topic, tells the customer, emits nothing"]},
    ],
    edges=[
        {"x1": 170, "y1": 100, "x2": 320, "y2": 100, "amber": True, "label": "orders.placed", "ly": -10},
        {"x1": 320, "y1": 145, "x2": 170, "y2": 145, "dashed": True, "label": "inventory.insufficient", "ly": 14},
        {"x1": 470, "y1": 100, "x2": 620, "y2": 100, "amber": True, "label": "inventory.reserved", "ly": -10},
        {"x1": 620, "y1": 145, "x2": 470, "y2": 145, "dashed": True, "label": "payments.failed", "ly": 14},
        {"x1": 770, "y1": 100, "x2": 920, "y2": 100, "amber": True, "label": "payments.processed", "ly": -10},
        {"x1": 95, "y1": 170, "x2": 95, "y2": 290},
        {"x1": 395, "y1": 170, "x2": 395, "y2": 290},
        {"x1": 695, "y1": 170, "x2": 695, "y2": 290},
        {"x1": 995, "y1": 170, "x2": 995, "y2": 290},
    ],
    notes=[
        {"x": 545, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Five services, one step each, joined only by Kafka topics"},
        {"x": 545, "y": 385, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "Topics carry the edp. prefix. Shipping also emits shipping.scheduled, in-transit and delivered; order also emits orders.cancelled."},
    ],
)
