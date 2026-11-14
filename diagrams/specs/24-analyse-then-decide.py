"""A carrier's note goes through the model once, is recorded, is decided by the rule unit and recorded again; a replay reads the recorded analyses and goes straight to the rules."""

from generate_diagram import emit

emit(
    "24-analyse-then-decide",
    960,
    460,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 130, "fill": "#fff8ef", "label": "Live path: the model analyses once per note"},
        {"x": 20, "y": 230, "w": 920, "h": 130, "fill": "#fafafa", "label": "Replay path: the rules decide again from the record, no model call"},
    ],
    nodes=[
        {"x": 35, "y": 100, "w": 140, "h": 70, "style": "box", "lines": ["delivery-exceptions", "carrier note, attempt"]},
        {"x": 205, "y": 100, "w": 160, "h": 70, "style": "kernel", "lines": ["AI service", "model: one reason"]},
        {"x": 395, "y": 100, "w": 150, "h": 70, "style": "accent", "lines": ["exception-analyses", "reason, model, prompt"]},
        {"x": 575, "y": 100, "w": 130, "h": 70, "style": "kernel", "lines": ["DecisionUnit", "rules"]},
        {"x": 735, "y": 100, "w": 190, "h": 70, "style": "accent", "lines": ["exception-decisions", "RESHIP or REFUND, rule name"]},
        {"x": 35, "y": 270, "w": 330, "h": 70, "style": "ghost", "lines": ["POST /demo/replay", "demo code: the model is never called"]},
        {"x": 395, "y": 270, "w": 150, "h": 70, "style": "accent", "lines": ["exception-analyses", "read from offset 0"]},
        {"x": 575, "y": 270, "w": 130, "h": 70, "style": "kernel", "lines": ["DecisionUnit", "same rules"]},
        {"x": 735, "y": 270, "w": 190, "h": 70, "style": "box", "lines": ["same decisions", "published nowhere"]},
        {"x": 40, "y": 390, "w": 880, "h": 44, "style": "ink", "lines": ["The record holds the model digest and prompt version; the decision holds the rule name"]},
    ],
    edges=[
        {"x1": 175, "y1": 135, "x2": 205, "y2": 135},
        {"x1": 365, "y1": 135, "x2": 395, "y2": 135},
        {"x1": 545, "y1": 135, "x2": 575, "y2": 135},
        {"x1": 705, "y1": 135, "x2": 735, "y2": 135},
        {"x1": 365, "y1": 305, "x2": 395, "y2": 305, "dashed": True},
        {"x1": 545, "y1": 305, "x2": 575, "y2": 305, "amber": True},
        {"x1": 705, "y1": 305, "x2": 735, "y2": 305, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The model analyses, the rules decide, both are recorded"},
    ],
)
