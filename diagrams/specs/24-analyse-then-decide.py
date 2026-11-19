"""A carrier's note is a message that passes through a Message Translator (the model, free text to a closed reason), then a Content-Based Router (the rules deciding RESHIP or REFUND); a replay reads the recorded analysis and goes straight to the router."""

from generate_diagram import emit

emit(
    "24-analyse-then-decide",
    980,
    480,
    bands=[
        {"x": 20, "y": 60, "w": 940, "h": 140, "fill": "#fff8ef", "label": "Live path: the model analyses once per note"},
        {"x": 20, "y": 240, "w": 940, "h": 140, "fill": "#fafafa", "label": "Replay path: the rules decide again from the record, no model call"},
    ],
    nodes=[
        {"x": 760, "y": 95, "w": 160, "h": 90, "style": "accent", "lines": ["exception-decisions", "RESHIP or REFUND, rule name"]},
        {"x": 40, "y": 275, "w": 340, "h": 90, "style": "ghost", "lines": ["POST /demo/replay", "the model is never called"]},
        {"x": 760, "y": 275, "w": 160, "h": 90, "style": "box", "lines": ["same decisions", "published nowhere"]},
        {"x": 40, "y": 400, "w": 900, "h": 44, "style": "ink", "lines": ["The record holds the model digest and prompt version; the decision holds the rule name"]},
    ],
    icons=[
        {"x": 40, "y": 95, "w": 160, "h": 90, "kind": "message", "label": "carrier note, attempt"},
        {"x": 220, "y": 95, "w": 160, "h": 90, "kind": "translator", "label": "model: note to reason"},
        {"x": 400, "y": 95, "w": 160, "h": 90, "kind": "channel", "label": "exception-analyses: reason, model, prompt"},
        {"x": 580, "y": 95, "w": 160, "h": 90, "kind": "router", "label": "rules: RESHIP or REFUND"},
        {"x": 400, "y": 275, "w": 160, "h": 90, "kind": "channel", "label": "exception-analyses: read from offset 0"},
        {"x": 580, "y": 275, "w": 160, "h": 90, "kind": "router", "label": "rules: same decision"},
    ],
    edges=[
        {"x1": 200, "y1": 140, "x2": 220, "y2": 140},
        {"x1": 380, "y1": 140, "x2": 400, "y2": 140},
        {"x1": 560, "y1": 140, "x2": 580, "y2": 140},
        {"x1": 740, "y1": 140, "x2": 760, "y2": 140},
        {"x1": 380, "y1": 320, "x2": 400, "y2": 320, "dashed": True},
        {"x1": 560, "y1": 320, "x2": 580, "y2": 320, "amber": True},
        {"x1": 740, "y1": 320, "x2": 760, "y2": 320, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The model analyses, the rules decide, both are recorded"},
    ],
)
