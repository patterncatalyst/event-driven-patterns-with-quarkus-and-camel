"""The Camel orchestrator as a Process Manager: the LRA coordinator is the hub, order-service opens and closes the saga with it, commands and replies travel to each participant over Kafka channels, and participants register their own undo with the coordinator."""

from generate_diagram import emit

emit(
    "23-camel-saga-lra",
    950,
    500,
    nodes=[
        {"x": 380, "y": 50, "w": 200, "h": 140, "style": "accent",
         "lines": ["order-service",
                   "opens the saga, MANUAL",
                   "complete or compensate"]},
        {"x": 40, "y": 310, "w": 220, "h": 80, "style": "box", "lines": ["inventory-service", "SUPPORTS, release"]},
        {"x": 710, "y": 310, "w": 220, "h": 80, "style": "box", "lines": ["payment-service", "SUPPORTS, refund"]},
        {"x": 380, "y": 330, "w": 200, "h": 100, "style": "ink", "lines": ["LRA coordinator", "holds the saga, its time limit and the registered compensations"]},
    ],
    icons=[
        {"x": 230, "y": 200, "w": 130, "h": 90, "kind": "channel", "label": "edp.inventory.commands, reserved"},
        {"x": 600, "y": 200, "w": 130, "h": 90, "kind": "channel", "label": "edp.payments.commands, processed or failed"},
    ],
    edges=[
        {"x1": 430, "y1": 190, "x2": 295, "y2": 200, "bidir": True, "label": "commands, replies", "ly": -10},
        {"x1": 295, "y1": 290, "x2": 150, "y2": 310, "bidir": True},
        {"x1": 530, "y1": 190, "x2": 665, "y2": 200, "bidir": True, "label": "commands, replies", "ly": -10},
        {"x1": 665, "y1": 290, "x2": 820, "y2": 310, "bidir": True},
        {"x1": 260, "y1": 350, "x2": 380, "y2": 365, "dashed": True, "label": "join, compensate"},
        {"x1": 710, "y1": 350, "x2": 580, "y2": 365, "dashed": True, "label": "join, compensate"},
        {"x1": 480, "y1": 190, "x2": 480, "y2": 330, "amber": True, "bidir": True, "label": "open, then complete or compensate", "ly": -10},
    ],
    notes=[
        {"x": 475, "y": 22, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The coordinator owns the saga; participants register their own undo"},
        {"x": 475, "y": 478, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "ReserveStock and TakePayment travel as commands; each participant joins on consume and replies after acting"},
    ],
)
