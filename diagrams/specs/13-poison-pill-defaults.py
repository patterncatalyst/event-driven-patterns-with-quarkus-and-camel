"""What a poison record does when nothing is configured: SmallRye stops the channel, Camel skips the record."""

from generate_diagram import emit

emit(
    "13-poison-pill-defaults",
    960,
    500,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 190, "fill": "#fafafa", "label": "SmallRye channel, failure-strategy=fail (the default)"},
        {"x": 20, "y": 280, "w": 920, "h": 190, "fill": "#fff8ef", "label": "Camel route, default error handler"},
    ],
    nodes=[
        {"x": 40, "y": 110, "w": 200, "h": 70, "style": "box", "lines": ["bad record", "O-7002"]},
        {"x": 320, "y": 110, "w": 200, "h": 70, "style": "accent", "lines": ["nack: the channel", "stops, offset not committed"]},
        {"x": 600, "y": 110, "w": 320, "h": 70, "style": "ink", "lines": ["readiness DOWN", "O-7005 behind it is never read"]},
        {"x": 40, "y": 190, "w": 480, "h": 46, "style": "ghost", "lines": ["nothing is lost, and nothing moves until someone acts"]},
        {"x": 40, "y": 330, "w": 200, "h": 70, "style": "box", "lines": ["bad record", "O-7002"]},
        {"x": 320, "y": 330, "w": 200, "h": 70, "style": "accent", "lines": ["logged after 1 attempt", "offset committed"]},
        {"x": 600, "y": 330, "w": 320, "h": 70, "style": "ink", "lines": ["consumption continues", "the record exists nowhere else"]},
        {"x": 40, "y": 410, "w": 480, "h": 46, "style": "ghost", "lines": ["nothing blocks, and nothing is kept"]},
    ],
    edges=[
        {"x1": 240, "y1": 145, "x2": 320, "y2": 145},
        {"x1": 520, "y1": 145, "x2": 600, "y2": 145},
        {"x1": 240, "y1": 365, "x2": 320, "y2": 365},
        {"x1": 520, "y1": 365, "x2": 600, "y2": 365},
    ],
    notes=[
        {"x": 480, "y": 30, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "Unconfigured, a bad record either stops the consumer or disappears"},
    ],
)
