"""Who decides after a conflict: the server retries a command whose intent does not depend on state; a caller that decided from a state it read is told."""

from generate_diagram import emit

emit(
    "14-retry-or-report",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 180, "fill": "#fff8ef", "label": "No If-Match: the server retries"},
        {"x": 20, "y": 260, "w": 920, "h": 200, "fill": "#fafafa", "label": "If-Match: the caller decided from the version it read"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 200, "h": 70, "style": "box", "lines": ["POST .../cancellation", "no If-Match"]},
        {"x": 270, "y": 110, "w": 200, "h": 70, "style": "accent", "lines": ["OrderCommands", "@Retry on ConcurrencyConflict"]},
        {"x": 500, "y": 110, "w": 200, "h": 70, "style": "box", "lines": ["OrderTransaction", "new transaction, reload"]},
        {"x": 730, "y": 110, "w": 190, "h": 70, "style": "ink", "lines": ["200 at the next version", "or 409 with the reason"]},
        {"x": 40, "y": 320, "w": 200, "h": 70, "style": "box", "lines": ["POST .../cancellation", "If-Match: \"2\""]},
        {"x": 270, "y": 320, "w": 200, "h": 70, "style": "accent", "lines": ["evaluatePreconditions", "current ETag is \"3\""]},
        {"x": 500, "y": 320, "w": 200, "h": 70, "style": "box", "lines": ["no command runs", "nothing is appended"]},
        {"x": 730, "y": 320, "w": 190, "h": 70, "style": "ink", "lines": ["412 Precondition Failed", "the caller reads and decides"]},
    ],
    edges=[
        {"x1": 240, "y1": 145, "x2": 270, "y2": 145},
        {"x1": 470, "y1": 145, "x2": 500, "y2": 145},
        {"x1": 700, "y1": 145, "x2": 730, "y2": 145},
        {"x1": 240, "y1": 355, "x2": 270, "y2": 355},
        {"x1": 470, "y1": 355, "x2": 500, "y2": 355},
        {"x1": 700, "y1": 355, "x2": 730, "y2": 355},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Retry what does not depend on what the caller saw, report what does"},
    ],
)
