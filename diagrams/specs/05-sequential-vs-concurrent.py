"""One caller that waits for each acknowledgement sends one record per request; many callers share batches."""

from generate_diagram import emit

emit(
    "05-sequential-vs-concurrent",
    960,
    470,
    bands=[
        {"x": 20, "y": 60, "w": 920, "h": 150, "fill": "#fafafa", "label": "One caller, waits for each acknowledgement"},
        {"x": 20, "y": 235, "w": 920, "h": 170, "fill": "#fafafa", "label": "Many callers in flight"},
    ],
    nodes=[
        {"x": 40, "y": 100, "w": 200, "h": 70, "style": "box", "lines": ["caller sends record 1", "then waits"]},
        {"x": 300, "y": 100, "w": 200, "h": 70, "style": "ghost", "lines": ["batch of 1 record", "waits up to linger.ms"]},
        {"x": 560, "y": 100, "w": 180, "h": 70, "style": "box", "lines": ["request with 1 record", "acknowledged"]},
        {"x": 780, "y": 100, "w": 140, "h": 70, "style": "box", "lines": ["record 2", "sent next"]},
        {"x": 40, "y": 285, "w": 200, "h": 70, "style": "box", "lines": ["200 callers send", "records 1 to 200"]},
        {"x": 300, "y": 285, "w": 200, "h": 70, "style": "accent", "lines": ["shared batches fill", "before linger.ms ends"]},
        {"x": 560, "y": 285, "w": 180, "h": 70, "style": "accent", "lines": ["request with", "many records"]},
        {"x": 780, "y": 285, "w": 140, "h": 70, "style": "box", "lines": ["callers released", "on the ack"]},
    ],
    edges=[
        {"x1": 240, "y1": 135, "x2": 300, "y2": 135},
        {"x1": 500, "y1": 135, "x2": 560, "y2": 135},
        {"x1": 740, "y1": 135, "x2": 780, "y2": 135, "dashed": True},
        {"x1": 240, "y1": 320, "x2": 300, "y2": 320, "amber": True},
        {"x1": 500, "y1": 320, "x2": 560, "y2": 320, "amber": True},
        {"x1": 740, "y1": 320, "x2": 780, "y2": 320},
    ],
    notes=[
        {"x": 480, "y": 24, "anchor": "middle", "bold": True, "size": 15, "color": "#444444",
         "text": "linger.ms only helps when sends overlap"},
        {"x": 480, "y": 440, "anchor": "middle", "size": 11, "color": "#555555",
         "text": "A single caller pays the linger wait on every record and never fills a batch."},
    ],
)
