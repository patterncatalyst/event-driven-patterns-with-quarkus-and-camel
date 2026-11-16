"""When a change cannot be made compatible: a migration consumer converts the old topic into a new one, producers and consumers move, and the old topic is retired."""

from generate_diagram import emit

emit(
    "af-schema-new-topic",
    1000,
    360,
    bands=[
        {"x": 20, "y": 60, "w": 960, "h": 250, "fill": "#fafafa", "label": "A new topic for the new shape"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 170, "h": 80, "style": "kernel", "lines": ["old topic", "shape v1", "retired when retention passes"]},
        {"x": 280, "y": 110, "w": 190, "h": 80, "style": "box", "lines": ["migration consumer", "reads v1, writes v2"]},
        {"x": 540, "y": 110, "w": 170, "h": 80, "style": "kernel", "lines": ["new topic", "shape v2"]},
        {"x": 780, "y": 110, "w": 180, "h": 80, "style": "user", "lines": ["v2 consumers", "read the new topic"]},
        {"x": 280, "y": 220, "w": 190, "h": 60, "style": "user", "lines": ["v2 producer", "writes the new topic"]},
        {"x": 40, "y": 220, "w": 170, "h": 60, "style": "ghost", "lines": ["v1 producer", "stops after the cut"]},
    ],
    edges=[
        {"x1": 210, "y1": 150, "x2": 280, "y2": 150},
        {"x1": 470, "y1": 150, "x2": 540, "y2": 150, "amber": True},
        {"x1": 710, "y1": 150, "x2": 780, "y2": 150},
        {"x1": 470, "y1": 250, "x2": 625, "y2": 190},
        {"x1": 125, "y1": 220, "x2": 125, "y2": 190, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "An incompatible change gets its own topic"},
        {"x": 500, "y": 336, "anchor": "middle", "size": 12, "color": "#555555",
         "text": "The costs are a topic, a consumer, and a window with two sources of truth"},
    ],
)
