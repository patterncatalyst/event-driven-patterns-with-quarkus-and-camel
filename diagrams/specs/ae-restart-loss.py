"""Held records whose offsets were committed are lost at a restart; the per-order database buffer keeps them."""

from generate_diagram import emit

emit(
    "ae-restart-loss",
    980,
    360,
    nodes=[
        {"x": 30, "y": 120, "w": 180, "h": 90, "style": "box", "lines": ["Kafka", "502 to 506 consumed", "offset committed"]},
        {"x": 270, "y": 120, "w": 200, "h": 90, "style": "accent", "lines": ["Resequencer memory", "holds 502 to 506", "501 is missing"]},
        {"x": 530, "y": 120, "w": 170, "h": 90, "style": "kernel", "lines": ["Restart", "memory is gone"]},
        {"x": 760, "y": 120, "w": 190, "h": 90, "style": "ink", "lines": ["After the restart", "only 501 is applied", "502 to 506 not re-read"]},
        {"x": 270, "y": 260, "w": 430, "h": 70, "style": "user", "lines": ["Per-order buffer in PostgreSQL", "held rows survive the restart"]},
    ],
    edges=[
        {"x1": 210, "y1": 165, "x2": 270, "y2": 165, "amber": True},
        {"x1": 470, "y1": 165, "x2": 530, "y2": 165, "amber": True},
        {"x1": 700, "y1": 165, "x2": 760, "y2": 165, "amber": True},
        {"x1": 370, "y1": 210, "x2": 370, "y2": 260, "dashed": True},
    ],
    notes=[
        {"x": 490, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "The client commits offsets on a timer, not when the resequencer releases"},
    ],
)
