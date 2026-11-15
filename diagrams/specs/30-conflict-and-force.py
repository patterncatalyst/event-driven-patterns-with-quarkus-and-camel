"""A second writer conflicts on an owned field, and --force-conflicts moves the ownership."""

from generate_diagram import emit

emit(
    "30-conflict-and-force",
    1000,
    380,
    nodes=[
        {"x": 30, "y": 80, "w": 200, "h": 90, "style": "box", "lines": ["edp-apps applies", "limits.memory 384Mi", "owner: edp-apps"]},
        {"x": 280, "y": 80, "w": 200, "h": 90, "style": "accent", "lines": ["edp-platform applies", "limits.memory 512Mi", "conflict with edp-apps"]},
        {"x": 530, "y": 80, "w": 200, "h": 90, "style": "ghost", "lines": ["Nothing changes", "value stays 384Mi", "owner: edp-apps"]},
        {"x": 280, "y": 240, "w": 200, "h": 90, "style": "accent", "lines": ["--force-conflicts", "value 512Mi", "owner: edp-platform"]},
        {"x": 530, "y": 240, "w": 200, "h": 90, "style": "box", "lines": ["edp-apps applies again", "conflict with edp-platform", "until it forces"]},
        {"x": 780, "y": 240, "w": 190, "h": 90, "style": "box", "lines": ["--force-conflicts", "value 384Mi", "owner: edp-apps"]},
    ],
    edges=[
        {"x1": 230, "y1": 125, "x2": 280, "y2": 125},
        {"x1": 480, "y1": 125, "x2": 530, "y2": 125, "dashed": True},
        {"x1": 380, "y1": 170, "x2": 380, "y2": 240, "amber": True},
        {"x1": 480, "y1": 285, "x2": 530, "y2": 285},
        {"x1": 730, "y1": 285, "x2": 780, "y2": 285, "amber": True},
    ],
    notes=[
        {"x": 500, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "A conflict refuses the write; forcing it takes the field from the other manager"},
        {"x": 500, "y": 360, "anchor": "middle", "size": 13, "color": "#555555",
         "text": "A client-side apply skips the conflict and overwrites"},
    ],
)
