"""The Camel orchestrator: order-service opens a saga at the coordinator, commands and replies travel over Kafka with the saga header, participants join, and the coordinator calls each registered compensation over HTTP."""

from generate_diagram import emit

emit(
    "23-camel-saga-lra",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 250, "h": 400, "fill": "#fff8ef", "label": "order-service"},
        {"x": 300, "y": 60, "w": 220, "h": 400, "fill": "#fafafa", "label": "Kafka, saga header on every record"},
        {"x": 550, "y": 60, "w": 390, "h": 400, "fill": "#fafafa", "label": "Participants and coordinator"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 210, "h": 70, "style": "accent", "lines": ["CamelOrderSagaRoute", "saga REQUIRES_NEW, MANUAL, timeout"]},
        {"x": 40, "y": 220, "w": 210, "h": 70, "style": "box", "lines": ["CamelSagaRepliesRoute", "take payment, complete, compensate"]},
        {"x": 40, "y": 340, "w": 210, "h": 70, "style": "box", "lines": ["callback routes", "cancel order, complete order"]},
        {"x": 320, "y": 100, "w": 180, "h": 70, "style": "kernel", "lines": ["edp.inventory.commands", "edp.payments.commands"]},
        {"x": 320, "y": 220, "w": 180, "h": 70, "style": "kernel", "lines": ["edp.inventory.reserved", "edp.payments.processed, failed"]},
        {"x": 570, "y": 100, "w": 160, "h": 70, "style": "box", "lines": ["inventory-service", "SUPPORTS, release"]},
        {"x": 760, "y": 100, "w": 160, "h": 70, "style": "box", "lines": ["payment-service", "SUPPORTS, refund"]},
        {"x": 570, "y": 340, "w": 350, "h": 70, "style": "ink", "lines": ["LRA coordinator", "holds the saga, its time limit and the registered compensations"]},
    ],
    edges=[
        {"x1": 250, "y1": 135, "x2": 320, "y2": 135, "label": "1"},
        {"x1": 500, "y1": 135, "x2": 570, "y2": 135, "label": "2"},
        {"x1": 650, "y1": 170, "x2": 650, "y2": 340, "amber": True, "dashed": True, "label": "join"},
        {"x1": 840, "y1": 170, "x2": 840, "y2": 340, "amber": True, "dashed": True, "label": "join"},
        {"x1": 570, "y1": 160, "x2": 500, "y2": 235, "label": "3"},
        {"x1": 320, "y1": 255, "x2": 250, "y2": 255, "label": "4"},
        {"x1": 145, "y1": 290, "x2": 570, "y2": 360, "amber": True, "label": "complete or compensate"},
        {"x1": 570, "y1": 395, "x2": 250, "y2": 385, "amber": True, "label": "callbacks over HTTP"},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The coordinator owns the saga; participants register their own undo"},
        {"x": 480, "y": 485, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "1 ReserveStock, 2 consumed and joined, 3 reply, 4 TakePayment follows the same path"},
    ],
)
