"""The Strimzi operator applies the bootstrap Service server-side, so a foreign field stays and an owned field returns."""

from generate_diagram import emit

emit(
    "30-strimzi-ssa",
    960,
    360,
    nodes=[
        {"x": 20, "y": 70, "w": 190, "h": 90, "style": "accent", "lines": ["strimzi-kafka-operator", "applies the Service", "each reconciliation"]},
        {"x": 20, "y": 220, "w": 190, "h": 90, "style": "box", "lines": ["edp-foreign", "adds example.com/owner", "no conflict"]},
        {"x": 370, "y": 60, "w": 230, "h": 250, "style": "ink", "lines": ["Service edp-kafka-bootstrap", "", "strimzi.io/discovery", "owner: the operator", "", "example.com/owner", "owner: edp-foreign"]},
        {"x": 740, "y": 70, "w": 200, "h": 90, "style": "box", "lines": ["Foreign annotation", "survives the", "reconciliations"]},
        {"x": 740, "y": 220, "w": 200, "h": 90, "style": "accent", "lines": ["Forced change of", "strimzi.io/discovery", "is restored"]},
    ],
    edges=[
        {"x1": 210, "y1": 115, "x2": 370, "y2": 115, "amber": True},
        {"x1": 210, "y1": 265, "x2": 370, "y2": 265},
        {"x1": 600, "y1": 115, "x2": 740, "y2": 115},
        {"x1": 600, "y1": 265, "x2": 740, "y2": 265, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 28, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The operator owns its fields and leaves everyone else's alone"},
    ],
)
