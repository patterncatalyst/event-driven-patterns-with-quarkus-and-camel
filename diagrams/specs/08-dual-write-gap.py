"""Two writes with no shared transaction: whichever order, a failure between them leaves the database and Kafka disagreeing."""

from generate_diagram import emit

emit(
    "08-dual-write-gap",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 190, "fill": "#fafafa", "label": "Commit first, then publish"},
        {"x": 20, "y": 280, "w": 920, "h": 190, "fill": "#fafafa", "label": "Publish first, then commit"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 200, "h": 70, "style": "box", "lines": ["order-service", "POST /orders"]},
        {"x": 320, "y": 110, "w": 200, "h": 70, "style": "box", "lines": ["1. commit the order", "PostgreSQL"]},
        {"x": 600, "y": 110, "w": 200, "h": 70, "style": "ghost", "lines": ["2. publish OrderPlaced", "Kafka"]},
        {"x": 320, "y": 190, "w": 480, "h": 46, "style": "accent", "lines": ["crash between 1 and 2: the order exists, no event ever announces it"]},
        {"x": 40, "y": 330, "w": 200, "h": 70, "style": "box", "lines": ["order-service", "POST /orders"]},
        {"x": 320, "y": 330, "w": 200, "h": 70, "style": "box", "lines": ["1. publish OrderPlaced", "Kafka"]},
        {"x": 600, "y": 330, "w": 200, "h": 70, "style": "ghost", "lines": ["2. commit the order", "PostgreSQL"]},
        {"x": 320, "y": 410, "w": 480, "h": 46, "style": "accent", "lines": ["commit fails after 1: the event announces an order that does not exist"]},
    ],
    edges=[
        {"x1": 240, "y1": 145, "x2": 320, "y2": 145},
        {"x1": 520, "y1": 145, "x2": 600, "y2": 145, "dashed": True},
        {"x1": 240, "y1": 365, "x2": 320, "y2": 365},
        {"x1": 520, "y1": 365, "x2": 600, "y2": 365, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "No transaction spans the database and the broker"},
    ],
)
