"""Each target is started fresh and read through its own tools; one comparison holds the allowlist."""

from generate_diagram import emit

emit(
    "ab-capture-and-compare",
    1000,
    400,
    nodes=[
        {"x": 20, "y": 70, "w": 220, "h": 70, "style": "box", "lines": ["verify.sh compose", "fresh containers, then read"]},
        {"x": 20, "y": 230, "w": 220, "h": 70, "style": "box", "lines": ["verify.sh k8s", "fresh namespaces, then read"]},
        {"x": 400, "y": 70, "w": 250, "h": 70, "style": "ink", "lines": ["target/captures/compose", "13 broker keys, 5 PostgreSQL"]},
        {"x": 400, "y": 230, "w": 250, "h": 70, "style": "ink", "lines": ["target/captures/k8s", "13 broker keys, 5 PostgreSQL"]},
        {"x": 730, "y": 135, "w": 260, "h": 100, "style": "accent", "lines": ["verify.sh compare", "equal, or an allowlisted", "difference, or a failure"]},
    ],
    edges=[
        {"x1": 240, "y1": 105, "x2": 400, "y2": 105, "amber": True, "label": "kafka-configs, SHOW", "ly": -10},
        {"x1": 240, "y1": 265, "x2": 400, "y2": 265, "amber": True, "label": "same tools, exec", "ly": -10},
        {"x1": 650, "y1": 105, "x2": 730, "y2": 165, "amber": True},
        {"x1": 650, "y1": 265, "x2": 730, "y2": 205, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Both captures are read from running systems; an empty capture fails"},
        {"x": 500, "y": 365, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "Four allowed differences, each with its reason in the text"},
    ],
)
