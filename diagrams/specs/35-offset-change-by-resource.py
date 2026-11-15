"""Offsets change through the KafkaConnector resource: stop, list, alter from a ConfigMap, resume; while running the alter is refused."""

from generate_diagram import emit

emit(
    "35-offset-change-by-resource",
    1000,
    440,
    nodes=[
        {"x": 30, "y": 150, "w": 170, "h": 90, "style": "accent", "lines": ["state: running", "tasks RUNNING"]},
        {"x": 300, "y": 150, "w": 170, "h": 90, "style": "box", "lines": ["state: stopped", "no tasks"]},
        {"x": 570, "y": 50, "w": 190, "h": 90, "style": "box", "lines": ["annotation list", "offsets.json in a", "ConfigMap"]},
        {"x": 570, "y": 250, "w": 190, "h": 90, "style": "box", "lines": ["annotation alter", "offsets from a", "ConfigMap"]},
        {"x": 810, "y": 150, "w": 160, "h": 90, "style": "ghost", "lines": ["Annotation", "removed by the", "operator"]},
        {"x": 30, "y": 330, "w": 440, "h": 80, "style": "ink", "lines": ["alter while running", "Warning event, annotation stays,", "offsets unchanged"]},
    ],
    edges=[
        {"x1": 200, "y1": 195, "x2": 300, "y2": 195, "amber": True},
        {"x1": 470, "y1": 175, "x2": 570, "y2": 110},
        {"x1": 470, "y1": 215, "x2": 570, "y2": 290},
        {"x1": 760, "y1": 95, "x2": 810, "y2": 175, "dashed": True},
        {"x1": 760, "y1": 295, "x2": 810, "y2": 215, "dashed": True},
        {"x1": 115, "y1": 240, "x2": 115, "y2": 330, "dashed": True},
        {"x1": 300, "y1": 215, "x2": 200, "y2": 215, "dashed": True},
    ],
    notes=[
        {"x": 500, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Every offset operation is a change to the resource"},
        {"x": 250, "y": 183, "anchor": "middle", "size": 12, "color": "#444444", "text": "stop"},
        {"x": 250, "y": 235, "anchor": "middle", "size": 12, "color": "#444444", "text": "state: running"},
    ],
)
