"""Free text goes in and one value of a closed set comes out: the schema, temperature 0, the seed and top-k 1 hold the answer, and an answer outside the set becomes UNKNOWN."""

from generate_diagram import emit

emit(
    "24-closed-answer",
    960,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 180, "fill": "#fff8ef", "label": "Three settings hold the answer in place"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 190, "h": 100, "style": "box", "lines": ["Free text", "Nobody home, card left,", "second attempt."]},
        {"x": 280, "y": 100, "w": 200, "h": 36, "style": "kernel", "lines": ["JSON schema with the enum"]},
        {"x": 280, "y": 144, "w": 200, "h": 36, "style": "kernel", "lines": ["temperature 0, top-k 1"]},
        {"x": 280, "y": 188, "w": 200, "h": 36, "style": "kernel", "lines": ["fixed seed"]},
        {"x": 530, "y": 110, "w": 170, "h": 100, "style": "box", "lines": ["Model", "a local Ollama", "one request per note"]},
        {"x": 750, "y": 110, "w": 170, "h": 100, "style": "accent", "lines": ["RECIPIENT_ABSENT", "and the evidence", "phrase"]},
        {"x": 40, "y": 280, "w": 420, "h": 70, "style": "ghost", "lines": ["Not about a delivery, or unsure", "the model answers UNKNOWN"]},
        {"x": 500, "y": 280, "w": 420, "h": 70, "style": "kernel", "lines": ["An answer the schema cannot parse", "becomes UNKNOWN and is counted"]},
    ],
    edges=[
        {"x1": 230, "y1": 160, "x2": 280, "y2": 160},
        {"x1": 480, "y1": 160, "x2": 530, "y2": 160},
        {"x1": 700, "y1": 160, "x2": 750, "y2": 160},
        {"x1": 835, "y1": 210, "x2": 710, "y2": 280, "dashed": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A closed answer from an open text"},
        {"x": 480, "y": 395, "anchor": "middle", "bold": False, "size": 12, "color": "#555555",
         "text": "A stable answer is not always a correct one: the rules see only the value, never whether it is right"},
    ],
)
