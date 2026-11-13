"""Two crafted pairs: close in event time but far apart in arrival, and far apart in event time but together in arrival. The Kafka Streams join decides on the first clock, the Camel aggregator on the second."""

from generate_diagram import emit

emit(
    "20-event-vs-arrival-time",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 170, "fill": "#fff8ef", "label": "O-2003: payment 2 minutes after the reservation in event time, 20 seconds later in arrival"},
        {"x": 20, "y": 260, "w": 920, "h": 170, "fill": "#fafafa", "label": "O-2004: payment 90 minutes after the reservation in event time, produced back to back"},
    ],
    nodes=[
        {"x": 40, "y": 105, "w": 200, "h": 80, "style": "box", "lines": ["Event times", "09:10 and 09:12", "2 minutes apart"]},
        {"x": 270, "y": 105, "w": 200, "h": 80, "style": "accent", "lines": ["Kafka Streams", "inside the 30 minute", "window: ReadyToShip"]},
        {"x": 500, "y": 105, "w": 200, "h": 80, "style": "box", "lines": ["Arrival", "20 seconds apart"]},
        {"x": 730, "y": 105, "w": 190, "h": 80, "style": "ink", "lines": ["Camel aggregator", "10 second timeout passed:", "PaymentOverdue first"]},
        {"x": 40, "y": 305, "w": 200, "h": 80, "style": "box", "lines": ["Event times", "09:20 and 10:50", "90 minutes apart"]},
        {"x": 270, "y": 305, "w": 200, "h": 80, "style": "ink", "lines": ["Kafka Streams", "outside the window:", "PaymentOverdue"]},
        {"x": 500, "y": 305, "w": 200, "h": 80, "style": "box", "lines": ["Arrival", "a few milliseconds apart"]},
        {"x": 730, "y": 305, "w": 190, "h": 80, "style": "accent", "lines": ["Camel aggregator", "both present at once:", "ReadyToShip"]},
    ],
    edges=[
        {"x1": 240, "y1": 145, "x2": 270, "y2": 145},
        {"x1": 700, "y1": 145, "x2": 730, "y2": 145},
        {"x1": 240, "y1": 345, "x2": 270, "y2": 345},
        {"x1": 700, "y1": 345, "x2": 730, "y2": 345},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One pair of clocks, two answers: the forms disagree in opposite directions"},
    ],
)
