"""A check is proved by breaking the code it guards: an altered copy must fail exactly the named test."""

from generate_diagram import emit

emit(
    "aa-bite-cycle",
    1000,
    330,
    nodes=[
        {"x": 20, "y": 90, "w": 170, "h": 90, "style": "box", "lines": ["Green suite", "every test passes", "on the real route"]},
        {"x": 235, "y": 90, "w": 170, "h": 90, "style": "accent", "lines": ["Alter one guard", "a patch applied to a", "copy, never the source"]},
        {"x": 450, "y": 90, "w": 170, "h": 90, "style": "box", "lines": ["Run the class", "broker from Dev Services", "fresh for each copy"]},
        {"x": 665, "y": 90, "w": 150, "h": 90, "style": "ink", "lines": ["Exactly one", "named test fails", "the others pass"]},
        {"x": 850, "y": 90, "w": 130, "h": 90, "style": "ghost", "lines": ["Copy dropped", "source unchanged"]},
        {"x": 235, "y": 235, "w": 385, "h": 55, "style": "kernel", "lines": ["A test that stays green is not guarding what it names"]},
    ],
    edges=[
        {"x1": 190, "y1": 135, "x2": 235, "y2": 135, "amber": True},
        {"x1": 405, "y1": 135, "x2": 450, "y2": 135, "amber": True},
        {"x1": 620, "y1": 135, "x2": 665, "y2": 135, "amber": True},
        {"x1": 815, "y1": 135, "x2": 850, "y2": 135},
        {"x1": 740, "y1": 180, "x2": 620, "y2": 255, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 40, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Break the code and see which test notices"},
    ],
)
