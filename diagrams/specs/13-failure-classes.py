"""Three failure classes of one record: unusable goes straight to the dead letter topic, transient is retried, exhausted retries end there too."""

from generate_diagram import emit

emit(
    "13-failure-classes",
    960,
    560,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 120, "fill": "#fafafa", "label": "Never succeeds: unparseable or invalid"},
        {"x": 20, "y": 200, "w": 920, "h": 150, "fill": "#fff8ef", "label": "Can succeed later: the row is locked"},
        {"x": 20, "y": 370, "w": 920, "h": 150, "fill": "#fafafa", "label": "Still failing after the last retry"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 230, "h": 56, "style": "box", "lines": ["edp.orders.placed", "truncated JSON, quantity 0"]},
        {"x": 365, "y": 100, "w": 230, "h": 56, "style": "accent", "lines": ["1 attempt, no retry", "abortOn / onException"]},
        {"x": 690, "y": 100, "w": 230, "h": 56, "style": "ink", "lines": ["edp.orders.placed-dlq", "original bytes"]},
        {"x": 40, "y": 250, "w": 230, "h": 56, "style": "box", "lines": ["edp.orders.placed", "valid order, row locked"]},
        {"x": 365, "y": 240, "w": 230, "h": 76, "style": "accent", "lines": ["PessimisticLockException", "retry after 1 s, new transaction", "each time"]},
        {"x": 690, "y": 250, "w": 230, "h": 56, "style": "box", "lines": ["stock reserved", "offset committed"]},
        {"x": 40, "y": 420, "w": 230, "h": 56, "style": "box", "lines": ["edp.orders.placed", "row locked for too long"]},
        {"x": 365, "y": 410, "w": 230, "h": 76, "style": "accent", "lines": ["retried until the", "limit is reached", "(1 + RETRY_MAX attempts)"]},
        {"x": 690, "y": 420, "w": 230, "h": 56, "style": "ink", "lines": ["edp.orders.placed-dlq", "original bytes"]},
    ],
    edges=[
        {"x1": 270, "y1": 128, "x2": 365, "y2": 128},
        {"x1": 595, "y1": 128, "x2": 690, "y2": 128},
        {"x1": 270, "y1": 278, "x2": 365, "y2": 278},
        {"x1": 595, "y1": 278, "x2": 690, "y2": 278},
        {"x1": 270, "y1": 448, "x2": 365, "y2": 448},
        {"x1": 595, "y1": 448, "x2": 690, "y2": 448},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Retry what can succeed later, dead-letter what never will"},
    ],
)
