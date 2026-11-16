"""Replicas on each broker: a new broker starts empty and receives replicas only through a rebalance."""

from generate_diagram import emit

emit(
    "33-scale-and-move",
    1040,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 1000, "h": 110, "fill": "#f6f8fa", "label": "Two brokers"},
        {"x": 20, "y": 185, "w": 1000, "h": 110, "fill": "#fff8ef", "label": "Pool grown to three: the new broker is empty"},
        {"x": 20, "y": 310, "w": 1000, "h": 110, "fill": "#f6f8fa", "label": "After the approved rebalance, and again after the operator's own at four"},
    ],
    nodes=[
        {"x": 60, "y": 100, "w": 200, "h": 55, "style": "box", "lines": ["broker A", "every replica"]},
        {"x": 300, "y": 100, "w": 200, "h": 55, "style": "box", "lines": ["broker B", "every replica"]},
        {"x": 60, "y": 225, "w": 200, "h": 55, "style": "box", "lines": ["broker A", "every replica"]},
        {"x": 300, "y": 225, "w": 200, "h": 55, "style": "box", "lines": ["broker B", "every replica"]},
        {"x": 540, "y": 225, "w": 200, "h": 55, "style": "ghost", "lines": ["new broker", "no replica"]},
        {"x": 60, "y": 350, "w": 200, "h": 55, "style": "box", "lines": ["broker A", "a share"]},
        {"x": 300, "y": 350, "w": 200, "h": 55, "style": "box", "lines": ["broker B", "a share"]},
        {"x": 540, "y": 350, "w": 200, "h": 55, "style": "accent", "lines": ["new broker", "a share"]},
        {"x": 780, "y": 350, "w": 200, "h": 55, "style": "accent", "lines": ["next new broker", "a share"]},
    ],
    edges=[
        {"x1": 400, "y1": 155, "x2": 400, "y2": 225, "dashed": True, "label": "pool 2 to 3", "ly": 0},
        {"x1": 640, "y1": 280, "x2": 640, "y2": 350, "amber": True, "label": "approved proposal", "ly": 0},
    ],
    notes=[
        {"x": 520, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Kafka places a partition when it is created; a new broker gets partitions only from a rebalance"},
        {"x": 520, "y": 450, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Scaling down reverses it: the operator drains a broker before it removes the pod"},
    ],
)
