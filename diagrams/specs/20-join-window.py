"""A reservation opens a 30 minute window in event time; a payment inside it matches, one outside it does not, and the unmatched reservation leaves only when stream time passes the window plus the grace."""

from generate_diagram import emit

emit(
    "20-join-window",
    960,
    430,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 150, "fill": "#fff8ef", "label": "Event time, read from the payload"},
        {"x": 20, "y": 240, "w": 920, "h": 150, "fill": "#fafafa", "label": "What the left join emits"},
    ],
    nodes=[
        {"x": 40, "y": 105, "w": 190, "h": 80, "style": "box", "lines": ["Reservation at T", "kept in the join store", "until the window ends"]},
        {"x": 260, "y": 105, "w": 200, "h": 80, "style": "accent", "lines": ["Payment at T + 2 min", "inside the 30 minute", "window"]},
        {"x": 490, "y": 105, "w": 200, "h": 80, "style": "ghost", "lines": ["Grace: 5 minutes", "a payment this late is", "still accepted"]},
        {"x": 720, "y": 105, "w": 200, "h": 80, "style": "ink", "lines": ["Stream time passes", "T + 35 min", "the window is closed"]},
        {"x": 260, "y": 285, "w": 200, "h": 70, "style": "accent", "lines": ["ReadyToShip", "reserved at T, paid at T + 2"]},
        {"x": 490, "y": 285, "w": 200, "h": 70, "style": "ghost", "lines": ["Payment at T + 90 min", "outside the window,", "no match"]},
        {"x": 720, "y": 285, "w": 200, "h": 70, "style": "ink", "lines": ["PaymentOverdue", "emitted by the record that", "moved stream time"]},
    ],
    edges=[
        {"x1": 230, "y1": 145, "x2": 260, "y2": 145},
        {"x1": 460, "y1": 145, "x2": 490, "y2": 145},
        {"x1": 690, "y1": 145, "x2": 720, "y2": 145},
        {"x1": 360, "y1": 185, "x2": 360, "y2": 285, "amber": True},
        {"x1": 820, "y1": 185, "x2": 820, "y2": 285},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The window is measured on the time of the fact, and only later records close it"},
    ],
)
