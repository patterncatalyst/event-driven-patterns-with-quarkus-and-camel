"""mvn verify runs the same route against a mocked analyst on Dev Services with no Ollama; a live run and a stability harness send the same note through the real model."""

from generate_diagram import emit

emit(
    "aj-ci-vs-live",
    960,
    420,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 130, "fill": "#fafafa", "label": "CI: mvn verify, no Ollama"},
        {"x": 20, "y": 220, "w": 920, "h": 130, "fill": "#fff8ef", "label": "Live run and the stability harness"},
    ],
    icons=[
        {"x": 35, "y": 94, "w": 110, "h": 90, "kind": "message", "label": "note"},
        {"x": 165, "y": 94, "w": 110, "h": 90, "kind": "channel", "label": "delivery-exceptions"},
        {"x": 35, "y": 254, "w": 110, "h": 90, "kind": "message", "label": "note"},
        {"x": 165, "y": 254, "w": 110, "h": 90, "kind": "channel", "label": "delivery-exceptions"},
        {"x": 585, "y": 254, "w": 110, "h": 90, "kind": "endpoint", "label": "real model"},
    ],
    nodes=[
        {"x": 315, "y": 90, "w": 170, "h": 80, "style": "kernel", "lines": ["mocked analyst", "golden prompt contract"]},
        {"x": 545, "y": 90, "w": 170, "h": 80, "style": "ghost", "lines": ["Ollama", "not started"]},
        {"x": 755, "y": 90, "w": 165, "h": 80, "style": "accent", "lines": ["exception-analyses", "deterministic"]},
        {"x": 315, "y": 250, "w": 170, "h": 80, "style": "kernel", "lines": ["DeliveryExceptionAnalyst", "@Tag(\"live\")"]},
        {"x": 755, "y": 250, "w": 165, "h": 80, "style": "accent", "lines": ["exception-analyses", "stability check"]},
    ],
    edges=[
        {"x1": 145, "y1": 130, "x2": 165, "y2": 130},
        {"x1": 275, "y1": 130, "x2": 315, "y2": 130},
        {"x1": 485, "y1": 110, "x2": 545, "y2": 110, "dashed": True, "label": "never started"},
        {"x1": 485, "y1": 150, "x2": 755, "y2": 150, "label": "deterministic answer"},
        {"x1": 145, "y1": 290, "x2": 165, "y2": 290},
        {"x1": 275, "y1": 290, "x2": 315, "y2": 290},
        {"x1": 485, "y1": 290, "x2": 585, "y2": 290},
        {"x1": 695, "y1": 290, "x2": 755, "y2": 290, "amber": True},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "One route, two models: the mock holds CI still, Ollama answers live"},
    ],
)
